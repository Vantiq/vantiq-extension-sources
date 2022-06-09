#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""
This is a Vantiq connector.  This connector's function is to provide for the execution of
Python scripts sent as part of a query operation.  This connector will make a connection with the Vantiq
system, identifying the source(s) that it "represents."  Subsequently, VAIL code in the Vantiq server
can issue "queries" against that source, where those queries indicate the Python code to be executed.

That Python code will then be executed. The code being executed has the option of sending results back as part
of its execution, or by simply retuning on completion, whereby the connector can interrogate the values of
some global variables, returning those as the results of the execution.
"""

__author__ = 'fhcarter'
__copyright__ = "Copyright 2022, Vantiq, Inc."
__license__ = "MIT License"
__email__ = "support@vantiq.com"

__all__ = [
    'PyExecConnector',
    'Connectors',
    'VANTIQ_SECTION',
    'PYTHON_EXEC_SECTION',
    'GENERAL_SECTION',
    'CODE',
    'SCRIPT_RESULTS',
    'CACHE_CODE',
    'CONNECTOR_INFO',
    'EXEC_HANDLES_RETURN',
    'COMPILE_TIME',
    'EXECUTION_TIME',
    'TOTAL_TIME',
    'RETURN_RUNTIME_INFO'
]

import asyncio
import os
from collections import OrderedDict
import hashlib
import json
import logging
import traceback
from typing import Union
from urllib import parse
import uuid

from codetiming import Timer

from vantiqconnectorsdk import VantiqConnector, VantiqConnectorSet, VantiqSourceConnection, setup_logging
from vantiqsdk import Vantiq, VantiqResources, VantiqResponse, VantiqException

# Connector runtime Info & related fields
# Note these (& the data results) use Vantiq/Vail/Java conventions on naming (camel-cased)
CONNECTOR_INFO = 'connectorRuntimeInfo'
TOTAL_TIME = 'totalTime'
EXECUTION_TIME = 'executionTime'
COMPILE_TIME = 'compileTime'
QUERY_TIME = 'query_time'

NEW_CACHE_ENTRY = 'newCacheEntry'
CURRENT_CACHE_SIZE = 'currentCacheSize'
CACHE_CAPACITY = 'cacheCapacity'

# Data result fields
SCRIPT_RESULTS = 'pythonCallResults'

# Source Configuration Parameters:
VANTIQ_SECTION = 'vantiq'
PYTHON_EXEC_SECTION = 'pythonExecConfig'
GENERAL_SECTION = 'general'
CODE_CACHE_SIZE = 'codeCacheSize'
RETURN_RUNTIME_INFO = 'returnRuntimeInformation'

# Compiled code cache entries
CACHE_ENTRY_SIGNATURE = 'signature'
CACHE_MODIFICATION_DATE = 'modDate'
CACHE_ENTRY_CODE = 'code'

# Interface parameters with Vantiq Server
CODE = 'code'  # Code --> Literally the code to run
SCRIPT = 'script'  # Script --> a document name from which to fetch the code to run
REPLACE_CACHE_ENTRY = 'replace'
CACHE_CODE = 'cache_code'
LIMIT_RETURN_TO = 'limitReturnTo'
EXEC_HANDLES_RETURN = 'codeHandlesReturn'
PRESET_VALUES = 'presetValues'


class CodeCache:
    """This class manages the cache of "compiled" code that this connector deals with."""

    # initialising capacity
    def __init__(self, capacity: int):
        self._cache = OrderedDict()
        self._capacity = capacity
        self._lock = asyncio.Lock()

    def __len__(self):
        return len(self._cache)

    def get_capacity(self):
        return self._capacity

    async def set_capacity(self, capacity: int):
        """Defines the maximum size for this cache. When set, adjust the size of the cache to reflect the new value."""
        self._capacity = capacity
        async with self._lock:
            while len(self._cache) > self._capacity:
                self._cache.popitem(last=False)

    async def get(self, key: str):
        """Return the value of the key from the cache

        The return value is the value stored.  -1 is returned if there is no entry for the key.
        In addition, querying for the key constitutes usage, we mark this key as used for LRU purposes.

        Parameters:
            key : str
                Key of the item desired
        Returns:
            Value of the stored item if found, -1 if not found.
        """
        async with self._lock:
            if key not in self._cache:
                return None
            else:
                self._cache.move_to_end(key)
                return self._cache[key]

    async def put(self, key: str, value) -> None:
        """Add / update the stored value for the key

        Update the stored value, marking it as the most recently used.  While here,
        check the cache capacity and remove the least-recently used value if
        we are over capacity.

        Parameters:
            key : str
                The key of the value to be stored
            value : *
                The value to be stored for the key.
        """
        async with self._lock:
            self._cache[key] = value
            self._cache.move_to_end(key)
            if len(self._cache) > self._capacity:
                self._cache.popitem(last=False)

    async def remove(self, key: str) -> None:
        """Remove the specified key

        Parameters:
            key : str
                Key of the value to be removed
        """
        try:
            async with self._lock:
                self._cache.pop(key, None)
        except KeyError:
            # If entity doesn't exist, consider it removed
            pass


def getBooleanValue(value) -> bool:
    """Convert string to boolean value.

    Case notwithstanding, 'yes', 'true', 't', and '1' are considered true.  Everything else is false.

    Parameters:
        value : str | bool
            Value to be converted
    Returns:
        Boolean value of value passed in, as per rules outlined above.
    """
    if isinstance(value, str):
        return value.lower() in ("yes", "true", "t", "1")
    elif isinstance(value, bool):
        return value
    else:
        return False


class PyExecConnector:
    """This class represents a single connector.  It provides handlers as required, responding to Vantiq messages
    as required.
    """

    def __init__(self, conn: VantiqSourceConnection):
        self.is_open = False
        self.source_name = None
        self.server_config = None
        self.source_config = None
        self.connection = conn
        self._code_cache = None
        self._return_runtime_info = False
        self.logger = logging.getLogger('Vantiq.PyExecConnector')
        self.vantiq_client: Union[Vantiq, None] = None
        self._lock = asyncio.Lock()
        self.user_tasks = []

    async def close_handler(self, ctx: dict):
        """Record that this connector is now closed, and release resources as appropriate"""
        self.is_open = False
        self.source_config = None
        if self.vantiq_client:
            await self.vantiq_client.close()
            self.vantiq_client = None

    async def connect_handler(self, ctx: dict, config: dict):
        """Note that we are connected & save the name & config."""
        self.source_config = config
        if 'config' in config.keys():
            config = config['config']
        cache_size = 128
        py_exec_config = None
        if PYTHON_EXEC_SECTION in config.keys() and GENERAL_SECTION in config[PYTHON_EXEC_SECTION].keys():
            py_exec_config = config[PYTHON_EXEC_SECTION][GENERAL_SECTION]
            if CODE_CACHE_SIZE in py_exec_config.keys():
                cache_size = py_exec_config[CODE_CACHE_SIZE]
            if RETURN_RUNTIME_INFO in py_exec_config.keys():
                self._return_runtime_info = getBooleanValue(py_exec_config[RETURN_RUNTIME_INFO])

        self._code_cache = CodeCache(cache_size)
        self.source_name = ctx[VantiqConnector.SOURCE_NAME]
        self.is_open = True

    async def publish_handler(self, ctx: dict, msg: dict):
        """The Vantiq server has published a message to this connector/source"""
        self.logger.warning('Connector for source %s: Unexpected call to publish handler -- context: %s, message: %s',
                            self.source_name, ctx, msg)

    def delete_user_task(self, user_task):
        print('Removing task from user_task list', user_task)
        try:
            self.user_tasks.remove(user_task)
        except ValueError:
            self.logger.warning('Delete of user task which was not present in user_tasks list: %s',
                                user_task)
        except Exception as e:
            self.logger.warning('Delete of user task failed unexpectedly: %s -- %s.', user_task, e)

    async def query_handler(self, ctx: dict, msg: dict):
        """The Vantiq server has sent a query message to this connector/source"""
        if not self.is_open:
            self.logger.error('Connector for source %s called when not open -- context: %s, message: %s',
                              self.source_name, ctx, msg)
            error = {VantiqConnector.ERROR_CODE: 'io.vantiq.pyexec.query.not.open',
                     VantiqConnector.ERROR_TEMPLATE: 'Connection to source {0} is current closed.',
                     VantiqConnector.ERROR_PARAMETERS: [self.source_name]}
            await self.connection.send_query_error(ctx, error)
            return

        code = None
        script = None
        if CODE in msg.keys():
            code = msg[CODE]
        elif SCRIPT in msg.keys():
            script = msg[SCRIPT]

        name = None
        if script:
            name = script
        elif 'name' in msg.keys():
            name = msg['name']

        # By default, if given a name, we will cache the result.  Can be overridden using cache_code = False
        cache_result = True and name is not None
        if CACHE_CODE in msg.keys():
            cache_result = msg[CACHE_CODE]
            cache_result = getBooleanValue(cache_result)

        exec_does_return = False
        if EXEC_HANDLES_RETURN in msg.keys():
            # Then our user is claiming that the called code will send the results back.
            # For this to happen, we'll have to pass the appropriate calls in, so
            # we need to remember that we're doing so.  Also, in this case, it's an error
            # to specify the values to return
            exec_does_return = getBooleanValue(msg[EXEC_HANDLES_RETURN])

        values_of_interest = None
        if LIMIT_RETURN_TO in msg.keys():
            values_of_interest = []
            vals = msg[LIMIT_RETURN_TO]
            if isinstance(vals, str):
                values_of_interest = vals.replace(' ', '').split(',')
            elif isinstance(vals, list):
                for v in vals:
                    val = v.strip()
                    values_of_interest.append(val)
            else:
                error_msg = {VantiqConnector.ERROR_CODE: 'io.vantiq.pyexecsource.runpython.badreturnvaluesfor',
                             VantiqConnector.ERROR_TEMPLATE:
                                 'The returnValuesFor parameter must be a string or a list, found: {0}',
                             VantiqConnector.ERROR_PARAMETERS: [type(vals).__name__]}
                await self.connection.send_query_error(ctx, error_msg)
                return
            if exec_does_return:
                error_msg = {VantiqConnector.ERROR_CODE: 'io.vantiq.pyexecsource.runpython.conflictingreturn',
                             VantiqConnector.ERROR_TEMPLATE:
                             'This query stated that the code will generate the return value and specified' +
                             'The returnValuesFor list. These items are in conflict.',
                             VantiqConnector.ERROR_PARAMETERS: []}
                await self.connection.send_query_error(ctx, error_msg)
                return

        if REPLACE_CACHE_ENTRY in msg.keys():
            replace_result = getBooleanValue(msg[REPLACE_CACHE_ENTRY])
            if replace_result and name:
                self._code_cache.remove(name)  # We're going to replace it, so just remove it now
        global_presets = {}
        if PRESET_VALUES in msg.keys():
            global_presets = msg[PRESET_VALUES]
            if not isinstance(global_presets, dict):
                error_msg = {VantiqConnector.ERROR_CODE: 'io.vantiq.pyexecsource.runpython.badglobalpreset',
                             VantiqConnector.ERROR_TEMPLATE:
                                 'The{0} entry must be VAIL object (a dict at the connector).',
                             VantiqConnector.ERROR_PARAMETERS: [PRESET_VALUES]}
                await self.connection.send_query_error(ctx, error_msg)
                return

        if cache_result and name is None:
            error_msg = {VantiqConnector.ERROR_CODE: 'io.vantiq.pyexecsource.runpython.nocachename',
                         VantiqConnector.ERROR_TEMPLATE:
                             'A request was made to cache the code but no name was provided.',
                         VantiqConnector.ERROR_PARAMETERS: []}
            await self.connection.send_query_error(ctx, error_msg)
        elif code is None and script is None and name is None:
            error_msg = {VantiqConnector.ERROR_CODE: 'io.vantiq.pyexecsource.runpython.nocode',
                         VantiqConnector.ERROR_TEMPLATE:
                             'No code was provided to execute. Message was {0}, but no {1} value was present.',
                         VantiqConnector.ERROR_PARAMETERS: [msg, CODE]}
            await self.connection.send_query_error(ctx, error_msg)
        elif code is not None and script:
            error_msg = {VantiqConnector.ERROR_CODE: 'io.vantiq.pyexecsource.runpython.ambiguouscode',
                         VantiqConnector.ERROR_TEMPLATE:
                             'Both the code and script parameters were specified.  Specify either one or the other. '
                             'Message was {0}.',
                         VantiqConnector.ERROR_PARAMETERS: [msg]}
            await self.connection.send_query_error(ctx, error_msg)
        elif script and name != script:
            error_msg = {VantiqConnector.ERROR_CODE: 'io.vantiq.pyexecsource.runpython.ambiguousname',
                         VantiqConnector.ERROR_TEMPLATE:
                             'A query was made to source {0} including both the script and name parameters.'
                             'When script is provided, no name is permitted.  Message as {1}.',
                         VantiqConnector.ERROR_PARAMETERS: [ctx[VantiqConnector.SOURCE_NAME], msg]}
            await self.connection.send_query_error(ctx, error_msg)
        else:
            task_name = f'user_code-{name if name is not None else "unnamed"}-{str(uuid.uuid4())}'
            # Here we don't await -- we'll let things run at their own pace, but we don't want to single-thread.
            new_task = asyncio.create_task(
                self.run_python_code(ctx, code, script, name, cache_result,
                                     exec_does_return, values_of_interest, global_presets),
                name=task_name)
            # We need to do two things with the created tasks
            # 1) Save a reference so that the task is not garbage collected while it is running
            self.user_tasks.append(new_task)
            # 2) Add a callback that will remove it from the list when completed so that it can be garbage collected
            #    as appropriate
            new_task.add_done_callback(self.delete_user_task)

    def establish_handlers(self):
        """Set up the handlers for this connector."""
        self.connection.configure_handlers(self.close_handler, self.connect_handler,
                                           self.publish_handler, self.query_handler)

    def sanitize_url(self, url: str) -> str:
        """Convert from websocket to http one, as appropriate."""
        parsed = parse.urlparse(url)
        if parsed[0] == 'wss':
            scheme = 'https'
        elif parsed[0] == 'ws':
            scheme = 'http'
        else:
            scheme = parsed[0]
        return f'{scheme}://{parsed[1]}'

    async def fetch_script(self, script_to_fetch: str):
        """Query the Vantiq server for the named document.  This is used to fetch
        Python code stored in a Vantiq document.

        Parameters:
             script_to_fetch : str
                The name of the document to fetch from Vantiq.
        Returns:
            tuple consisting of a boolean indicating whether this was found in the cache,
                the date that code (document) was last changed,
                the code itself, and
                an error if things did not go as planned.
        """
        error = None
        try:
            # Only one request should get the Vantiq connection.  Once created, it can be used
            # without conflict.
            await self._lock.acquire()
            if self.vantiq_client is None:
                config = self.connection.get_server_config()
                url = config[VantiqConnector.TARGET_SERVER]
                # We may need to sanitize the URL here.
                url = self.sanitize_url(url)
                self.vantiq_client = Vantiq(url)
                await self.vantiq_client.set_access_token(config[VantiqConnector.AUTH_TOKEN])
        except VantiqException as ve:
            if self.vantiq_client is not None:
                # noinspection PyBroadException
                try:
                    await self.vantiq_client.close()
                except Exception:
                    pass
                finally:
                    self.vantiq_client = None
            error = {VantiqConnector.ERROR_CODE: ve.code,
                     VantiqConnector.ERROR_TEMPLATE: ve.message,
                     VantiqConnector.ERROR_PARAMETERS: ve.params}
        except Exception as e:
            if self.vantiq_client is not None:
                # noinspection PyBroadException
                try:
                    await self.vantiq_client.close()
                except Exception:
                    pass
                finally:
                    self.vantiq_client = None

            error = {VantiqConnector.ERROR_CODE: 'io.vantiq.pyexecsource.vantiqconnectfail',
                     VantiqConnector.ERROR_TEMPLATE: repr(e),
                     VantiqConnector.ERROR_PARAMETERS: []}
        finally:
            self._lock.release()
        if error:
            return False, None, None, error

        # Here, we are connected to Vantiq.  Let's go fetch the document, using its mod date to validate the cache
        content_to_fetch = None
        mod_date = None
        vr: VantiqResponse = await self.vantiq_client.select_one(VantiqResources.DOCUMENTS, script_to_fetch)
        if vr.is_success:
            doc: dict = vr.body
            if 'isIncomplete' in doc.keys() and doc['isIncomplete']:
                error = {VantiqConnector.ERROR_CODE: 'io.vantiq.pyexecsource.docincomplete',
                         VantiqConnector.ERROR_TEMPLATE: 'Document {0} is incomplete and cannot be used.',
                         VantiqConnector.ERROR_PARAMETERS: [script_to_fetch]}
            elif doc['contentSize'] <= 0:
                error = {VantiqConnector.ERROR_CODE: 'io.vantiq.pyexecsource.doclength',
                         VantiqConnector.ERROR_TEMPLATE: 'Document {0} has an invalid length.',
                         VantiqConnector.ERROR_PARAMETERS: [script_to_fetch, doc['contentSize']]}
            else:
                if 'ars_modifiedAt' in doc.keys():
                    mod_date = doc['ars_modifiedAt']
                else:
                    mod_date = doc['ars_createdAt']
                content_to_fetch = doc['content']
        else:
            for err in vr.errors:
                self.logger.error(f'Error fetching Document {script_to_fetch} from Vantiq: '
                                  f'code: {err.code}, message: {err.message}, params: {err.params}')
            error = {VantiqConnector.ERROR_CODE: vr.errors[0].code,
                     VantiqConnector.ERROR_TEMPLATE: vr.errors[0].message,
                     VantiqConnector.ERROR_PARAMETERS: vr.errors[0].params}
        if error:
            return False, None, None, error

        # If we get here, we think there's content to run.  Let's check the cache first
        entry = await self._code_cache.get(script_to_fetch)
        using_cached = False

        if entry:
            if mod_date != entry[CACHE_MODIFICATION_DATE]:
                # Then the code has changed.  Remove it & continue...
                await self._code_cache.remove(script_to_fetch)
                using_cached = False
            else:
                code = entry[CACHE_ENTRY_CODE]
                using_cached = True
                return using_cached, mod_date, code, error
        # If we get here, then we need to fetch the content...
        vr = await self.vantiq_client.download(content_to_fetch)
        if not vr.is_success:
            for err in vr.errors:
                self.logger.error(f'Error fetching Document {script_to_fetch} content from Vantiq: '
                                  f'code: {err.code}, message: {err.message}, params: {err.params}')
            error = {VantiqConnector.ERROR_CODE: vr.errors[0].code,
                     VantiqConnector.ERROR_TEMPLATE: vr.errors[0].message,
                     VantiqConnector.ERROR_PARAMETERS: vr.errors[0].params}
            return False, None, None, error
        else:
            if vr.body:
                raw_bytes = await vr.body.read()   # Get all the data.
                code = raw_bytes.decode('utf-8')
            else:
                error = {VantiqConnector.ERROR_CODE: 'io.vantiq.pyexecsource.doccontent.empty',
                         VantiqConnector.ERROR_TEMPLATE: 'Document content for {0} was empty.',
                         VantiqConnector.ERROR_PARAMETERS: [script_to_fetch]}
                return False, None, None, error
        # We leave the Vantiq client connection open as we'll likely need it again.
        return using_cached, mod_date, code, error

    async def run_python_code(self, ctx: dict, code_text: str, script_to_fetch: str, name: str, cache_result: bool,
                              exec_does_return: bool, desired_results: [str], preset_globals: dict):
        """Run Python code as instructed.

        Run the code provided, returning values or errors as required.

        Parameters:
            ctx : dict
                The context for this query.  This context is used to send responses or errors.
            code_text : str
                The code to run, if appropriate.  For some simple cases, user can pass the code directly.
                Most will use the script_to_fetch to obtain the code from a document.
            script_to_fetch : str
                The name of the Vantiq document from which to fetch the code to run.  We will use the modification
                date of that document to determine whether to use the cached compiled code or to re-fetch and
                rebuild the cache entry.
            name : str
                Name to use for this code.  This is used only when code_text is not None.
            cache_result: bool
                Whether to cache the result.  Rarely used.
            exec_does_return : bool
                If true, indicates that the code to be run will handle the responses to the query. That is, if true,
                the code running will make the appropriate send_result() & send_error() calls.  If false,
                the connector will send back the global variable values, limited by "desired_results".
            desired_results : List[str]
                List if names of the global variables to return.  Used only when exec_does_return is false.
            preset_globals : dict
                A set of starting values to be passed into the code. For each item in the dict, the key is the name
                of the global variable, and its value the value of said variable.
        Returns:
            None
        """
        if script_to_fetch:
            self.logger.debug('Executing python code from: %s', script_to_fetch)
        else:
            self.logger.debug('Executing python code: %s', code_text)
        try:
            compiled_code = None
            using_cached = False
            cached_new = False
            error_msg = None
            faux_file_path = None
            compile_time = None
            with Timer('Connector Timer') as connector_timer:
                connector_timer.logger = None
                if script_to_fetch:
                    using_cached, mod_date, result, error_msg = await self.fetch_script(script_to_fetch)
                    if error_msg is None:
                        if using_cached:
                            compiled_code = result
                        else:
                            code_text = result
                        faux_file_path = script_to_fetch
                    else:
                        await self.connection.send_query_error(ctx, error_msg)
                        return
                elif name:
                    cache_entry = await self._code_cache.get(name)
                    if cache_entry:
                        compiled_code = cache_entry[CACHE_ENTRY_CODE]
                        using_cached = True
                    if name.endswith('.py'):
                        faux_file_path = name
                    else:
                        faux_file_path = name + '.py'
                    if code_text is not None:
                        signer = hashlib.new('sha256')
                        signer.update(code_text.encode('utf-8'))
                        sig = signer.hexdigest()
                        if cache_entry:
                            if sig != cache_entry[CACHE_ENTRY_SIGNATURE]:
                                # Then the code has changed for this name.  Update accordingly...
                                compiled_code = None
                                await self._code_cache.remove(name)
                                using_cached = False
                if compiled_code is None:
                    if code_text is None and name:
                        error_msg = {VantiqConnector.ERROR_CODE: 'io.vantiq.pyexecsource.runpython.nocache',
                                     VantiqConnector.ERROR_TEMPLATE:
                                         'No cached code was found for name: {0}.',
                                     VantiqConnector.ERROR_PARAMETERS: [name]}
                    elif code_text is None:
                        error_msg = {VantiqConnector.ERROR_CODE: 'io.vantiq.pyexecsource.runpython.nocode',
                                     VantiqConnector.ERROR_TEMPLATE:
                                         'No code was provided to execute.',
                                     VantiqConnector.ERROR_PARAMETERS: []}
                if error_msg:
                    await self.connection.send_query_error(ctx, error_msg)
                    return
                else:
                    if compiled_code is None:
                        faux_file_path = f'code-for-source-{self.source_name}-{uuid.uuid4()}' \
                            if script_to_fetch is None else script_to_fetch
                        with Timer(COMPILE_TIME) as compile_timer:
                            try:
                                compile_timer.logger = None
                                compiled_code = compile(code_text, faux_file_path, 'exec')
                            except SyntaxError as se:
                                error_msg = {VantiqConnector.ERROR_CODE: 'io.vantiq.pyexecsource.compile.syntaxerror',
                                             VantiqConnector.ERROR_TEMPLATE:
                                                 'Compilation resulted in: {0} :: file {1}, line {2}, offset {3} -- {4}',
                                             VantiqConnector.ERROR_PARAMETERS: [type(se).__name__,
                                                                                se.filename,
                                                                                se.lineno,
                                                                                se.offset,
                                                                                se.text]}
                            except ImportError as ie:
                                error_msg = {VantiqConnector.ERROR_CODE: 'io.vantiq.pyexecsource.compile.importerror',
                                             VantiqConnector.ERROR_TEMPLATE:
                                                 'Compilation resulted in: {0} :: {1}',
                                             VantiqConnector.ERROR_PARAMETERS: [type(ie).__name__, ie]}
                            except ImportWarning as ie:
                                error_msg = {VantiqConnector.ERROR_CODE: 'io.vantiq.pyexecsource.compile.importwarning',
                                             VantiqConnector.ERROR_TEMPLATE:
                                                 'Compilation resulted in: {0} :: {1}',
                                             VantiqConnector.ERROR_PARAMETERS: [type(ie).__name__, ie]}
                            except Exception as e:
                                error_msg = {VantiqConnector.ERROR_CODE: 'io.vantiq.pyexecsource.compile.exception',
                                             VantiqConnector.ERROR_TEMPLATE:
                                                 'Compilation resulted in: {0} :: {1}',
                                             VantiqConnector.ERROR_PARAMETERS: [type(e).__name__, e]}
                            finally:
                                if error_msg:
                                    await self.connection.send_query_error(ctx, error_msg)
                                    return
                        compile_time = compile_timer.last
                        self.logger.debug('Using just-compiled code for name: %s', name)
                    # Else we're using cached code

                    if name and cache_result and code_text is not None and not using_cached:
                        self.logger.debug('Caching code for name: %s', name)
                        cached_new = True
                        if script_to_fetch is None:
                            signer = hashlib.new('sha256')
                            signer.update(code_text.encode('utf-8'))
                            sig = signer.hexdigest()
                            cached_entry = {CACHE_ENTRY_SIGNATURE: sig,
                                            CACHE_MODIFICATION_DATE: None,
                                            CACHE_ENTRY_CODE: compiled_code}
                        else:
                            cached_entry = {CACHE_ENTRY_SIGNATURE: None,
                                            CACHE_MODIFICATION_DATE: mod_date,
                                            CACHE_ENTRY_CODE: compiled_code}
                        await self._code_cache.put(name, cached_entry)

                global_vars = {
                    '__file__': faux_file_path,
                    '__name__': '__main__',
                    'connector_connection': self.connection,
                    'connector_context': ctx.copy()
                }
                # Set any global variables to pass in values.
                for name, val in preset_globals.items():
                    # The presence of these values has already been validated
                    global_vars[name] = val

                with Timer('Execution Time') as exec_timer:
                    exec_timer.logger = None
                    try:
                        exec(compiled_code, global_vars, None)
                    except ImportError as ie:
                        error_msg = {VantiqConnector.ERROR_CODE: 'io.vantiq.pyexecsource.execution.importerror',
                                     VantiqConnector.ERROR_TEMPLATE:
                                         'Execution raised exception: {0} :: {1}',
                                     VantiqConnector.ERROR_PARAMETERS: [type(ie).__name__, ie.msg]}
                    except ImportWarning as ie:
                        error_msg = {VantiqConnector.ERROR_CODE: 'io.vantiq.pyexecsource.execution.importwarning',
                                     VantiqConnector.ERROR_TEMPLATE:
                                         'Executing code raised exception: {0} :: {1}',
                                     VantiqConnector.ERROR_PARAMETERS: [type(ie).__name__, ie.msg]}
                    except Exception as exc:
                        # Our user code had a problem. Assume the worst and finish things up for them
                        error_msg = {VantiqConnector.ERROR_CODE: 'io.vantiq.pyexecsource.execution.exception',
                                     VantiqConnector.ERROR_TEMPLATE:
                                         'Executing code raised exception: {0} :: {1}',
                                     VantiqConnector.ERROR_PARAMETERS: [type(exc).__name__,
                                                                        str(traceback.format_exc())]}
                        if isinstance(exc, MemoryError):
                            # If we've gotten the purportedly unrecoverable out of memory error, we'll declare ourselves
                            # unhealthy.  In a K8s environment, we'll get restarted (assuming they who've deployed us
                            # set the probes up correctly). Otherwise, we'll continue. If things are really recoverable,
                            # we'll recover. Otherwise, exit will be called and someone will restart us.
                            await self.connection.declare_unhealthy()
                    finally:
                        if error_msg:
                            await self.connection.send_query_error(ctx, error_msg)
                            return
                exec_time = exec_timer.last

            query_time = connector_timer.last
            connector_info = None
            if self._return_runtime_info:
                # If our configuration asks us to return runtime information, do so
                connector_time = connector_timer.last
                connector_info = {'using_cached': using_cached}
                if name:
                    connector_info['name'] = name
                connector_info[NEW_CACHE_ENTRY] = cached_new and not using_cached
                if compile_time:
                    connector_info[COMPILE_TIME] = compile_time
                if exec_time:
                    connector_info[EXECUTION_TIME] = exec_time
                if connector_time:
                    connector_info[TOTAL_TIME] = connector_time
                if query_time:
                    connector_info[QUERY_TIME] = query_time
                connector_info[CURRENT_CACHE_SIZE] = len(self._code_cache)
                connector_info[CACHE_CAPACITY] = self._code_cache.get_capacity()

            if not exec_does_return:
                msg_to_return = {}
                for name, value in global_vars.items():
                    if name not in ['__file__', '__name__', 'connector_context', 'connector_connection']:
                        # self.logger.debug('Found global var %s: type: %s, value: %s', name, type(value), value)
                        # Ensure that we can encode the values on the way back
                        if desired_results is None or name in desired_results:
                            try:
                                # we try & serialize to ensure that what we're returning can be serialized
                                # If there's an exception, skip this value.  We don't use the results otherwise.
                                json.dumps(value)
                                msg_to_return[name] = value
                            except (TypeError, OverflowError):
                                # If we get an error, we'll skip this value
                                self.logger.debug('Could not encode global %s to JSON.  Skipping it.', name)

                ret_msg = {SCRIPT_RESULTS: msg_to_return}
                if connector_info:
                    ret_msg[CONNECTOR_INFO] = connector_info
                await self.connection.send_query_response(ctx, VantiqConnector.QUERY_COMPLETE, ret_msg)
        except Exception as exc:
            self.logger.exception('Execution of Python code resulted in exception.')
            error_msg = {VantiqConnector.ERROR_CODE: 'io.vantiq.pyexecsource.runpython.exception',
                         VantiqConnector.ERROR_TEMPLATE:
                             'Executing python code in connector resulted in an exception: {0} :: {1}',
                         VantiqConnector.ERROR_PARAMETERS: [type(exc).__name__, str(traceback.format_exc())]}
            if isinstance(exc, MemoryError):
                # If we've gotten the purportedly unrecoverable out of memory error, we'll declare ourselves
                # unhealthy. In a K8s environment, we'll get restarted (assuming they who've deployed us
                # set the probes up correctly). Otherwise, we'll continue. If things are really recoverable,
                # we'll recover. Otherwise, exit will be called and someone will restart us.
                await self.connection.declare_unhealthy()
            await self.connection.send_query_error(ctx, error_msg)


class Connectors:
    """This is the set of connectors managed by this running instance."""

    def __init__(self):
        self.connector_set = VantiqConnectorSet()
        self.logger = logging.getLogger('Vantiq.PyExecConnector')
        self.logger.setLevel(logging.DEBUG)  # TODO:  Remove this after a  bit of burn-in.  Would prefer more data now.

    async def run(self):
        """Run the connectors.

        Sets up the connector set & runs things.  Generally does not return.
        """
        vantiq_connectors = self.connector_set.get_connections().items()
        sources = []
        for name, conn in vantiq_connectors:
            self.logger.info('Creating PyExecConnector for connection to source: %s', name)
            pec = PyExecConnector(conn)
            pec.establish_handlers()
            sources.append(name)

        self.logger.info('Running PyExecConnector.')
        running_in_k8s = os.getenv('KUBERNETES_SERVICE_HOST')
        if running_in_k8s:
            self.logger.info('Performing declare_healthy() action.')
            await self.connector_set.declare_healthy()
        plural = "s" if len(sources) > 1 else ""
        startup_message = f'Running connector{plural} for Vantiq source{plural} ' \
                          f'{",".join(sources)}{" in Kubernetes" if running_in_k8s else ""}'
        print(startup_message)
        self.logger.info(startup_message)

        await self.connector_set.run_connectors()
        await self.connector_set.close()


def main():
    setup_logging()
    connectors = Connectors()
    asyncio.run(connectors.run())


if __name__ == '__main__':
    main()

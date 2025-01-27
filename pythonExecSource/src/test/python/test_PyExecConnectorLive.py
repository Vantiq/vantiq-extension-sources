__author__ = 'fhcarter'
__copyright__ = "Copyright 2022, Vantiq, Inc."
__license__ = "MIT License"
__email__ = "support@vantiq.com"


import asyncio
from asyncio import CancelledError
import os
import traceback
from typing import Union

import pytest

from vantiqconnectorsdk import VantiqConnector, VantiqSourceConnection, setup_logging
from vantiqsdk import Vantiq, VantiqResponse, VantiqResources
from pyExecConnector \
    import Connectors, COMPILE_TIME, CONNECTOR_INFO, EXECUTION_TIME, \
    GENERAL_SECTION, PYTHON_EXEC_SECTION, RETURN_RUNTIME_INFO, SCRIPT_RESULTS, TOTAL_TIME

loginit = """
[loggers]
keys=root

[handlers]
keys=consoleHandler

[formatters]
keys=simpleFormatter

[logger_root]
level=INFO
handlers=consoleHandler

[handler_consoleHandler]
class=StreamHandler
level=DEBUG
formatter=simpleFormatter
args=(sys.stdout,)

[formatter_simpleFormatter]
format=%(asctime)s - %(name)s - %(levelname)s - %(message)s
datefmt=
"""

TEST_SELF_RETURNING = 'testSelfReturning'
py_code_self_returning = \
"""
import os
import asyncio
import uuid

three = 3
somevar = {'message': 'from Python'}
where_are_we = os.getcwd()

async def looper(count):
    global connector_context
    global connector_connection
    print('Looper: Context: ', connector_context)
    print('Looper: Our source is: ', connector_connection.get_source())

    try:
        for i in range(count-1):
            print('Looper: In loop, returning results for iteration: ', i)
            await connector_connection.send_query_response(connector_context, 100, 
                                                           {'item': 'I am list item {0}'.format(i)})
        print('Looper: In loop, returning final result')
        await connector_connection.send_query_response(connector_context, 200, {'item': 'I am the last item'})
        await connector_connection.send_notification({'notify_msg':
                                        'note this is a notification: ' + str(uuid.uuid4())})

        print('Connector connection is: ', connector_connection)
        print('Looper: Loop completed')
    except Exception as exc:
        print('Looper TRAPPED EXCEPTION', exc)

def main():
    print('Looper -- in main')
    loop = asyncio.get_event_loop()
    tasks = []
    tasks.append(loop.create_task(looper(loop_count)))
    print('Looper -- about to wait for the loop')
    print('Looper -- done with wait')

    
if __name__ == '__main__':
    main()
"""

TEST_TYPE = 'PyConnectorTestType'
TEST_RULE = 'PyConnectorTestRule'
TEST_SIMPLE_CODE = 'performSimpleCode'
TEST_SIMPLE_DOC = 'testSimpleDoc'
PYTHON_TEST_DOC = 'pythonDocFullOfCode'
py_code_simple = \
"""
import os

some_list = []
three = 3
somevar = {'message': 'from Python'}
where_are_we = os.getcwd()

def looper(count):
    global some_list
    for i in range(count):
         some_list.append('I am list item {0}'.format(i))

if __name__ == '__main__':
    looper(loop_count)
"""

TEST_SYNTAX_ERROR = 'testSyntaxError'
py_code_syntax_error = \
"""
import os

some_list = { 'error': 'oopsy' ]
three = 3
somevar = {'message': 'from Python'}
where_are_we = os.getcwd()

def looper(count):
    global some_list
    for i in range(count):
         some_list.append('I am list item {0}'.format(i))

if __name__ == '__main__':
    looper(loop_count)
"""

TEST_INDENT_ERROR = 'testIndentError'
py_code_indent_error = \
"""
import os

some_list = []
three = 3
somevar = {'message': 'from Python'}
where_are_we = os.getcwd()

def looper(count):
global some_list
for i in range(count):
some_list.append('I am list item {0}'.format(i))

if __name__ == '__main__':
    looper(loop_count)
"""

TEST_IMPORT_ERROR = 'testImportError'
py_code_import_error = \
"""
from os import os_bogus_module_which_does_not_exist

some_list = []
three = 3
somevar = {'message': 'from Python'}
where_are_we = os.getcwd()

def looper(count):
    global some_list
    # use the bogus import
    os_bogus_module_which_does_not_exist.do_something()
    for i in range(count):
         some_list.append('I am list item {0}'.format(i))

if __name__ == '__main__':
    looper(loop_count)
"""

TEST_TIMEOUT = 'testTimeout'
py_code_timeout = \
"""
import time

def looper(count):
    for i in range(count):
        print('Waiting 1 second of', count)
        time.sleep(1)
        # await asyncio.sleep(1)
     
def main():
    print('Looper -- in main')
    print('Looper -- about to wait for the loop')
    looper(loop_count)
    print('Looper -- done with wait')

if __name__ == '__main__':
    main()
"""

py_code_verify_liveness = \
"""
i_am_alive = 'dead'
def main():
    global i_am_alive
    i_am_alive = 'Because I think'
    print('i_am_alive = ', i_am_alive)
    
if __name__ == '__main__':
    main()
"""

_server_url: Union[str, None] = None
_access_token: Union[str, None] = None
_username: Union[str, None] = None
_password: Union[str, None] = None
PY_EXEC_SOURCE_NAME = 'pythonTestSource'
PY_EXEC_SOURCE_TYPE = 'PYTHONEXECUTION'
PY_EXEC_SOURCE_IMPL = {
   "name": "PYTHONEXECUTION",
   "baseType": "EXTENSION",
   "verticle": "service:extensionSource",
   "config": {},
   "baseConfigProperties": []
}

QUERY_COUNT = 20


@pytest.fixture(autouse=True)
def pytest_sessionstart():
    global _server_url
    global _access_token
    global _username
    global _password
    _server_url = os.getenv('VANTIQ_URL')
    _access_token = os.getenv('VANTIQ_ACCESS_TOKEN')
    _username = os.getenv('VANTIQ_USERNAME')
    _password = os.getenv('VANTIQ_PASSWORD')


if not os.path.exists('serverConfig'):
    os.makedirs('serverConfig')
lg = open('serverConfig/logger.ini', mode='wt')
lg.write(loginit)
lg.close()


async def do_some_notifies(count: int, conn: VantiqSourceConnection):
    while not conn.is_connected:
        await asyncio.sleep(0.5)

    for i in range(count):
        # noinspection PyBroadException
        try:
            await conn.send_notification({'hi': 'from Python: {0}'.format(i)})
        except Exception:
            pass
            print(traceback.format_exc())


async def run_client_test(conn_set: Connectors):
    conn = asyncio.create_task(conn_set.run())
    tasks = [conn]
    # In these tests, notifies are done by client code, so we need not have a task to send them
    # as we do in the SDK test.
    await asyncio.gather(*tasks)


class TestLiveConnection:

    async def setup_test_env(self, client: Vantiq, use_conn_info: bool):
        # noinspection PyBroadException
        try:
            vr: VantiqResponse = await client.delete_one(VantiqResources.DOCUMENTS, PYTHON_TEST_DOC)
            vr = await client.delete_one('system.procedures', TEST_SIMPLE_CODE)
            vr = await client.delete_one('system.procedures', TEST_SIMPLE_DOC)
            vr = await client.delete_one('system.procedures', TEST_SYNTAX_ERROR)
            vr = await client.delete_one('system.procedures', TEST_INDENT_ERROR)
            vr = await client.delete_one('system.procedures', TEST_IMPORT_ERROR)
            vr = await client.delete_one('system.procedures', TEST_TIMEOUT)

            vr = await client.delete_one(VantiqResources.RULES, TEST_RULE)
            vr = await client.delete_one(VantiqResources.TYPES, TEST_TYPE)

            vr = await client.upsert(VantiqResources.SOURCE_IMPLS, PY_EXEC_SOURCE_IMPL)
            self.dump_result('Creating source impl', vr)
            assert vr.is_success
            cfg = {'config':
                   {PYTHON_EXEC_SECTION:
                    {GENERAL_SECTION:
                     {RETURN_RUNTIME_INFO: str(use_conn_info)}
                     }
                    }
                   }
            src_def = {'name': PY_EXEC_SOURCE_NAME, 'type': PY_EXEC_SOURCE_TYPE,
                       'config': cfg, 'active': True}
            vr = await client.upsert(VantiqResources.SOURCES, src_def)
            self.dump_result('Creating Source', vr)
            assert vr.is_success

            proc_def = {'ruleText': f'''PROCEDURE {TEST_SIMPLE_CODE}()
    var pythonCode = "{py_code_simple}"

    var result = select * from source {PY_EXEC_SOURCE_NAME}
        with name = "py_code_simple", code = pythonCode, presetValues = {{ loop_count: 20 }}
    log.info("Got result: {{}}", [result])
    return result
    '''}
            vr = await client.insert('system.procedures', proc_def)
            assert vr.is_success
            assert vr.errors is None or vr.errors == []

            file_content = py_code_self_returning
            doc = {'name': PYTHON_TEST_DOC, 'fileType': 'text/x-python', 'content': file_content}
            vr = await client.insert(VantiqResources.DOCUMENTS, doc)
            assert isinstance(vr, VantiqResponse)
            assert vr.is_success
            self.dump_result('Creating Document', vr)
            assert vr.is_success

            proc_def = {'ruleText': f'''PROCEDURE {TEST_SIMPLE_DOC}()
                var result
                var allResults = []
                for (result in select * from source {PY_EXEC_SOURCE_NAME}
                    with script = "{PYTHON_TEST_DOC}", codeHandlesReturn = true,
                        presetValues = {{ loop_count: 20 }}) {{
                    log.info("Got (partial) document result: {{}}", [result])
                    allResults.push(result)
                }}
                return allResults
                '''}
            vr = await client.insert('system.procedures', proc_def)
            assert vr.is_success
            assert vr.errors is None or vr.errors == []

            # Set up procedures to verify compile error processing
            for k, v in {TEST_SYNTAX_ERROR: py_code_syntax_error,
                         TEST_INDENT_ERROR: py_code_indent_error,
                         TEST_IMPORT_ERROR: py_code_import_error}.items():
                proc_def = {'ruleText': f'''PROCEDURE {k}()
                var pythonCode = "{v}"
    
                var result = select * from source {PY_EXEC_SOURCE_NAME}
                    with name = "py_code_timeout", code = pythonCode, presetValues = {{ loop_count: 3 }},
                        codeHandlesReturn = true
                log.info("Got result: {{}}", [result])
                return result
                '''}
                vr = await client.insert('system.procedures', proc_def)
                assert vr.is_success
                assert vr.errors is None or vr.errors == []

            proc_def = {'ruleText': f'''PROCEDURE {TEST_TIMEOUT}()
            var pythonCode = "{py_code_timeout}"

            try {{
                // Note that this code does NOT handle returning information.  Which will cause the timeout.
                var result = select * from source {PY_EXEC_SOURCE_NAME}
                    with name = "py_code_timeout", code = pythonCode, presetValues = {{loop_count: 3}},
                        codeHandlesReturn = true
                    log.info("Got result: {{}}", [result])
            }} catch (timeoutError) {{
                if (timeoutError.code != "io.vantiq.sourcemgr.request.timeout") {{
                    exception("pyexec.test.failure", "query did not time out.  Instead {0}", [timeoutError])
                }}
            }}
            
            // Having performed a timeout, verify that the connector is still up & responsive
            pythonCode = "{py_code_verify_liveness}"

            var result = select * from source {PY_EXEC_SOURCE_NAME}
                with name = "py_code_verify_liveness", code = pythonCode, presetValues = {{ i_am_alive: "I hope" }}
            log.info("Got result: {{}}", [result])

            return result
            '''}
            vr = await client.insert('system.procedures', proc_def)
            assert vr.is_success
            assert vr.errors is None or vr.errors == []

            test_type_def = {"name": TEST_TYPE,
                             "properties": {
                                 "notify_msg": {"type": "String"}
                             },
                             }
            vr = await client.insert(VantiqResources.TYPES, test_type_def)
            assert vr.is_success

            rule = {'ruleText': f"""RULE {TEST_RULE}

            WHEN EVENT OCCURS ON "/sources/{PY_EXEC_SOURCE_NAME}" AS event

            log.info("Source Event {{}}", [event.value])

            INSERT INTO {TEST_TYPE}(event.value)
            """}
            vr = await client.insert(VantiqResources.RULES, rule)
            assert vr.is_success
        except Exception:
            print('Unable to setup environment:', traceback.format_exc())

    async def update_document(self, client: Vantiq, doc_name: str):
        file_content = py_code_self_returning.replace("'item'", "'line_item'")
        doc = {'name': doc_name, 'fileType': 'text/x-python', 'content': file_content}
        vr = await client.insert(VantiqResources.DOCUMENTS, doc)
        self.dump_result('Updating Document', vr)
        assert vr.is_success

    @pytest.fixture(autouse=True)
    def _setup(self):
        """This method replaces the usual __init__(self).  __init__(self) is not supported by pytest.
        Its primary purpose here is to 'declare' (via assignment) the instance variables.
        """
        self._message_count = 0
        self._close_count = 0
        self._connect_count = 0
        self._publish_count = 0
        self._query_count = 0
        self._is_connected = False
        self.vantiq_connection: Union[Vantiq, None] = None
        self.connector_task = None

    @classmethod
    def setup_class(cls):
        # noinspection PyBroadException
        try:
            if not os.path.exists('serverConfig'):
                os.makedirs('serverConfig')
            log_init = open('serverConfig/logger.ini', mode='wt')
            log_init.write(loginit)
            log_init.close()
            if os.path.exists('VantiqConnector.log'):
                os.remove('VantiqConnector.log')
            setup_logging()
        except Exception:
            pass

    def dump_result(self, tag: str, vr: VantiqResponse):
        print(f'{tag} response: ', str(vr))

    async def run_server_test(self, config, qry_count: int, conn_info: bool, proc_name:str,
                              expected_error: Union[dict, None], expected_result: Union[dict, None] = None):

        assert self.vantiq_connection is not None
        await asyncio.sleep(1)
        doc_changed = False
        if not expected_error and not expected_result:
            for i in range(qry_count):
                vr: VantiqResponse = await self.vantiq_connection.execute(proc_name, {})
                self.dump_result(f'Exec {proc_name}', vr)
                assert vr.is_success
                assert vr.body is not None
                assert isinstance(vr.body, list)
                assert isinstance(vr.body[0], dict)
                if proc_name == TEST_SIMPLE_CODE:
                    res: dict = vr.body[0]
                    assert SCRIPT_RESULTS in res
                    pcr = res[SCRIPT_RESULTS]
                    assert 'somevar' in pcr
                    assert 'where_are_we' in pcr
                    assert 'some_list' in pcr
                    assert len(pcr['some_list']) == qry_count

                    if conn_info:
                        assert CONNECTOR_INFO in res
                        ci = res[CONNECTOR_INFO]
                        assert TOTAL_TIME in ci
                        assert EXECUTION_TIME in ci
                        if i == 0:
                            assert COMPILE_TIME in ci
                        else:
                            assert COMPILE_TIME not in ci
                    else:
                        assert CONNECTOR_INFO not in res
                elif proc_name == TEST_SIMPLE_DOC:
                    assert len(vr.body) == qry_count
                    for j in range(len(vr.body)):
                        line: dict = vr.body[j]
                        if doc_changed:
                            assert 'line_item' in line
                        else:
                            assert 'item' in line

                    if i == qry_count // 2:
                        # Update our document to change something we test.
                        # Do this to verify that our cache updates work as expected.
                        await self.update_document(self.vantiq_connection, PYTHON_TEST_DOC)
                        doc_changed = True

            if proc_name == TEST_SIMPLE_DOC:
                await asyncio.sleep(2)  # Let the rules run in Vantiq
                vr = await self.vantiq_connection.count(TEST_TYPE, {})
                assert vr.is_success
                print("Count result:", vr.count)
                assert vr.count >= qry_count - 1
        elif expected_result:
            vr: VantiqResponse = await self.vantiq_connection.execute(proc_name, {})
            self.dump_result(f'Exec {proc_name}', vr)
            assert vr.is_success
            assert vr.body is not None
            res: dict = vr.body[0]
            assert SCRIPT_RESULTS in res
            pcr: dict = res[SCRIPT_RESULTS]
            for k, v in expected_result.items():
                assert k in pcr.keys()
                assert v in pcr[k]
        else:
            vr: VantiqResponse = await self.vantiq_connection.execute(proc_name, {})
            self.dump_result(f'Exec {proc_name}', vr)
            assert not vr.is_success
            assert vr.body is None
            assert len(vr.errors) == 1
            assert vr.errors[0].code == expected_error[VantiqConnector.ERROR_CODE]
            assert expected_error[VantiqConnector.ERROR_TEMPLATE] in vr.errors[0].message

        if self.connector_task is not None:
            self.connector_task.cancel()

    async def do_connector_test_raw_code(self, use_conn_info: bool, qry_count: int, at_once: int):
        await self.do_connector_test(use_conn_info, qry_count, at_once, TEST_SIMPLE_CODE, None, None)

    async def do_connector_test_documents(self, use_conn_info: bool, qry_count: int, at_once: int):
        await self.do_connector_test(use_conn_info, qry_count, at_once, TEST_SIMPLE_DOC, None,
                                     None)

    async def do_connector_test_documents_connect_args(self, use_conn_info: bool, qry_count: int, at_once: int):
        await self.do_connector_test(use_conn_info, qry_count, at_once, TEST_SIMPLE_DOC, None,
                                     None, '{ "disableSslVerification": true }')

    async def do_connector_test(self, use_conn_info: bool, qry_count: int, at_once: int,
                                proc_to_run: str, expected_error: Union[dict, None],
                                expected_result: Union[dict, None], connect_args: str = None):
        # Construct a server config file to use...
        assert at_once == 0 or qry_count % at_once == 0  # Test config failure -- at once must be multiples
        cf = f'''
        targetServer={_server_url}
        authToken={_access_token}
        sources={PY_EXEC_SOURCE_NAME}
        '''
        if connect_args is not None:
            cf = cf + f'''
            {VantiqConnector.FAIL_ON_CONNECTION_ERROR}=true
            connectKWArgs={connect_args}
            '''

        if connect_args:
            # Here, we'll assume that we're disabling SSL, so we will, too.
            self.vantiq_connection = Vantiq(_server_url, "1", ssl=False)
        else:
            self.vantiq_connection = Vantiq(_server_url)
        await self.vantiq_connection.set_access_token(_access_token)
        await self.setup_test_env(self.vantiq_connection, use_conn_info)

        connectors = None
        try:
            scf = open('serverConfig/server.config', encoding='utf-8', mode='wt')
            scf.write(cf)
            filename = scf.name
            scf.close()
            connectors = Connectors()

            tasks = []
            loop = asyncio.get_event_loop()
            self.connector_task = loop.create_task(connectors.run())
            tasks.append(self.connector_task)

            tasks.append(loop.create_task(self.run_server_test(filename, qry_count,
                                                               use_conn_info, proc_to_run,
                                                               expected_error, expected_result)))
            try:
                await asyncio.gather(*tasks)
            except CancelledError:
                pass
            for t in tasks:
                if not t.done():
                    t.cancel()
        finally:
            os.remove('serverConfig/server.config')
            if connectors is not None:
                await connectors.connector_set.close()

        if isinstance(self.vantiq_connection, Vantiq):
            # close connection to avoid spurious warnings from the test framework.
            # Good code hygiene to do the close anyway
            await self.vantiq_connection.close()

    @staticmethod
    def check_test_conditions():
        if _server_url is None or _access_token is None or (_username is None and _password is None):
            pytest.skip('Need access to Vantiq server.')

    @pytest.mark.asyncio
    @pytest.mark.timeout(25)
    async def test_syntax_error(self):
        self.check_test_conditions()
        await self.do_connector_test(False, 1, 1, TEST_SYNTAX_ERROR,
                                     {VantiqConnector.ERROR_CODE: 'io.vantiq.pyexecsource.compile.syntaxerror',
                                      VantiqConnector.ERROR_TEMPLATE: 'SyntaxError'}, None)

    @pytest.mark.asyncio
    @pytest.mark.timeout(25)
    async def test_import_error(self):
        self.check_test_conditions()
        # Import errors (also subclass ModuleNotFound errors) are found at execution time, generally, rather
        # than compile time.  So we'll check them there as well as compile time.
        await self.do_connector_test(False, 1, 1, TEST_IMPORT_ERROR,
                                     {VantiqConnector.ERROR_CODE: 'io.vantiq.pyexecsource.execution.importerror',
                                      VantiqConnector.ERROR_TEMPLATE: 'ImportError'}, None)

    @pytest.mark.asyncio
    @pytest.mark.timeout(135)
    async def test_timeout_error(self):
        self.check_test_conditions()
        # Import errors (also subclass ModuleNotFound errors) are found at execution time, generally, rather
        # than compile time.  So we'll check them there as well as compile time.
        await self.do_connector_test(False, 1, 1, TEST_TIMEOUT, None, {'i_am_alive': 'Because I think'})

    @pytest.mark.asyncio
    @pytest.mark.timeout(25)
    async def test_indent_error(self):
        self.check_test_conditions()
        await self.do_connector_test(False, 1, 1, TEST_INDENT_ERROR,
                                     {VantiqConnector.ERROR_CODE: 'io.vantiq.pyexecsource.compile.syntaxerror',
                                      VantiqConnector.ERROR_TEMPLATE: 'IndentationError'}, None)

    @pytest.mark.asyncio
    @pytest.mark.timeout(25)
    async def test_simple_query(self):
        self.check_test_conditions()
        await self.do_connector_test_raw_code(True, QUERY_COUNT, 0)

    @pytest.mark.asyncio
    @pytest.mark.timeout(25)
    async def test_simple_query_noci(self):
        self.check_test_conditions()
        await self.do_connector_test_raw_code(False, QUERY_COUNT, 0)

    @pytest.mark.asyncio
    @pytest.mark.timeout(25)
    async def test_simple_query_multiple(self):
        self.check_test_conditions()
        await self.do_connector_test_raw_code(True, QUERY_COUNT, 5)

    @pytest.mark.asyncio
    @pytest.mark.timeout(25)
    async def test_simple_query_noci_multiple(self):
        self.check_test_conditions()
        await self.do_connector_test_raw_code(False, QUERY_COUNT, 5)

    @pytest.mark.asyncio
    @pytest.mark.timeout(25)
    async def test_doc_query(self):
        self.check_test_conditions()
        await self.do_connector_test_documents(True, QUERY_COUNT, 0)

    @pytest.mark.asyncio
    @pytest.mark.timeout(25)
    async def test_doc_query_connect_args(self):
        self.check_test_conditions()
        if _server_url.startswith('ws:') or _server_url.startswith('http:'):
            pytest.skip('This test requires an SSL-enabled server (https or wss).')
        await self.do_connector_test_documents_connect_args(True, QUERY_COUNT, 0)

    @pytest.mark.asyncio
    @pytest.mark.timeout(25)
    async def test_doc_query_multiple(self):
        self.check_test_conditions()
        await self.do_connector_test_documents(True, QUERY_COUNT, 5)

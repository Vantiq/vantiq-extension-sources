#!/usr/bin/env python
# -*- coding: utf-8 -*-

"""Vantiq Connector SDK for Python

This module contains the Vantiq Connector SDK for the Python language.

Structure:
    The SDK consists of two (2) primary classes.  These classes are as follows:
        VantiqSourceConnection -- this is a class holding the client connection for a single source
        VantiqConnectorSet -- this class manages the runtime connections for all sources instantiated by a single
            server.config file.
    
Constants:
    The following constants are available to support the callers use of the interface.

        Server response control
            VantiqConnector.QUERY_EMPTY -- used when a query returns no data
            VantiqConnector.QUERY_PARTIAL -- used when there will be more than one call to send_query_response()
            VantiqConnector.QUERY_COMPLETE -- used to indicate the last (or only) call to send_query_complete()
                              (when the response is not empty)

        server.config file properties
           VantiqConnector.SOURCES -- list of sources whose connection is defined by this config file
           VantiqConnector.TARGET_SERVER -- URI for the Vantiq server
           VantiqConnector.AUTH_TOKEN -- Access token to be used to connect.  This is generally better done by leaving
                         it out and using the CONNECTOR_AUTH_TOKEN environment variable
           CONNECTOR_AUTH_TOKEN -- Environment variable name from which to get the access token
           VantiqConnector.SEND_PINGS -- whether this connector should send pings periodically.  It is a good idea
                         to set this to true
               when the connector will have idle time. Some network connections will terminate when not used
               for a while.
"""

__author__ = 'fhcarter'
__copyright__ = "Copyright 2022, Vantiq, Inc."
__license__ = "MIT License"
__email__ = "support@vantiq.com"
__all__ = ['VantiqSourceConnection',
           'VantiqConnector',
           'VantiqConnectorSet',
           'VantiqConnectorConfigException',
           'VantiqConnectorException',
           'setup_logging',
           ]

import asyncio
import ssl
import uuid
from asyncio import Future
import json
import logging
import logging.config
from json import JSONDecodeError
from logging import Logger
import os
import re
import socket
import string
import sys
import traceback
from typing import Awaitable, Callable, Union
from urllib import parse

import jprops
import websockets

_OP_AUTHENTICATE = 'authenticate'  # used for user/pw
_OP_VALIDATE = 'validate'   # used for auth tokens
_OP_CONNECT_EXTENSION = 'connectExtension'
_OP_CONFIGURE_EXTENSION = 'configureExtension'
_OP_RECONNECT_REQUIRED = 'reconnectRequired'
_OP_PUBLISH = 'publish'
_OP_QUERY = 'query'
_OP_NOTIFY = 'notification'
_PARAM_RECONNECT_SECRET = 'reconnectSecret'
_CLOSED = 'connectionHasBeenClosed'  # Pseudo op used to track what's happened
_TEST_CLOSE = 'testRequestsClientClose'  # Used during tests
_CONNECTION_FAILED = 'connection_failed'

# Message content properties
# Internal use
_STATUS = 'status'
_BODY = 'body'
_RESPONSE_ADDRESS_HEADER = "X-Reply-Address"
_MESSAGE_HEADERS = 'messageHeaders'
_ORIGIN_ADDRESS = 'REPLY_ADDR_HEADER'

_OPERATION = 'op'
_RESOURCE_NAME = 'resourceName'
_RESOURCE_ID = 'resourceId'
_SOURCES_RESOURCE = 'sources'
_OBJECT = 'object'
_PARAMETERS = 'parameters'

_WEBSOCKET_URL_PATTERN = '.*/api/v[0-9]+/wsock/websocket'
_WEBSOCKET_V1_PATH = '/api/v1/wsock/websocket'

# Initialise the logging module
_vlog: Union[Logger, None] = None


def setup_logging():
    """Read the log configuration file & initialize appropriately"""
    global _vlog
    # load the logging configuration
    logger_config = 'serverConfig/logger.ini'
    if os.path.exists(logger_config):
        logging.config.fileConfig(logger_config, disable_existing_loggers=False)
    _vlog = logging.getLogger(__name__)
    _vlog.setLevel(logging.DEBUG)
    # create a file handler
    handler = logging.FileHandler('VantiqConnector.log')
    handler.setLevel(logging.INFO)

    # create a logging format
    formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
    handler.setFormatter(formatter)

    # add the file handler to the logger
    _vlog.addHandler(handler)


def sanitize_url(user_url):
    """Adjust the provided TARGET_SERVER URI, converting to use web sockets"""
    global _vlog
    ts = parse.urlparse(user_url)
    scheme = ts.scheme
    path = ts.path

    if ts.scheme.startswith('http'):
        # Need to replace the http with WS equivalents
        if ts.scheme.startswith('https'):
            scheme = 'wss'
        else:
            scheme = 'ws'

    if not re.fullmatch(_WEBSOCKET_URL_PATTERN, path):
        path = _WEBSOCKET_V1_PATH

    # noinspection PyProtectedMember
    clean_url = parse.urlunparse(ts._replace(path=path, scheme=scheme))
    _vlog.debug('Configured URL %s converted to %s', user_url, clean_url)
    return clean_url


class VantiqConnector:
    """A set of interface constants for use with the Vantiq Connector SDK"""
    # External Use

    ERROR_CODE = 'messageCode'
    ERROR_TEMPLATE = 'messageTemplate'
    ERROR_PARAMETERS = 'parameters'

    # Context properties
    SOURCE_NAME = 'source_name'
    RESPONSE_ADDRESS = 'response_address'

    # Status Codes
    QUERY_COMPLETE = 200
    QUERY_EMPTY = 204
    QUERY_PARTIAL = 100
    QUERY_ERROR = 400

    # Server Configuration properties
    SOURCES = 'sources'
    TARGET_SERVER = 'targetServer'
    AUTH_TOKEN = 'authToken'
    FIXED_RECONNECT_SECRET = 'reconnectSecret'
    CONNECTOR_AUTH_TOKEN = 'CONNECTOR_AUTH_TOKEN'
    SEND_PINGS = 'sendPings'
    FAIL_ON_CONNECTION_ERROR = 'failOnConnectionError'
    PORT_PROPERTY_NAME = "tcpProbePort"
    TCP_PROBE_PORT_DEFAULT = 8000
    CONNECT_KW_ARGS = 'connectKWArgs'
    DISABLE_SSL_VERIFICATION = 'disableSslVerification'


class VantiqConnectorException(RuntimeError):
    """An error from the connector/Vantiq interoperation."""
    pass  # No special handling required -- exception class is the marker


class VantiqConnectorConfigException(VantiqConnectorException):
    """An error in the connector configuration."""
    pass  # No special handling required -- exception class is the marker


class VantiqSourceConnection:
    """A connection to a single source

    This class manages the connection to and interactions with the Vantiq server on behalf a single source.

    Generally, users are encouraged to use the VanticConnectorSet class.  That class whill orchestrate the operation
    of the set of connections required for the sources defined in the server.config file.
    """

    _reconnectSecret = None

    def __init__(self, source_name: string, config: Union[dict, None]):
        self.source_name = source_name
        # TODO: when upgrade to python 3.10, use more compact specification
        self.close_handler: Union[Callable[[dict], Awaitable[None]], None] = None
        self.connect_handler: Union[Callable[[dict, dict], Awaitable[None]], None] = None
        self.publish_handler: Union[Callable[[dict, dict], Awaitable[None]], None] = None
        self.query_handler: Union[Callable[[dict, dict], Awaitable[None]], None] = None

        self.connection: websockets = None
        self.config = config
        self.is_connected = False
        self._is_connected_future = None
        self._connector_set = None
        # Initialize with empty to make usage easier.
        self.connect_kw_args: dict = {}
        self.disable_ssl_check = False
        if config is not None:
            fixedReconnectSecret : string = config.get(VantiqConnector.FIXED_RECONNECT_SECRET, None)
            if fixedReconnectSecret is None:
                self._reconnectSecret = source_name + '_' + str(uuid.uuid1())
            else:
                self._reconnectSecret = source_name + '_' + fixedReconnectSecret
            kwArgString: string = config.get(VantiqConnector.CONNECT_KW_ARGS)
            if kwArgString is not None:
                try:
                    kw_temp : dict = json.loads(kwArgString)
                    disable_ssl = kw_temp.get(VantiqConnector.DISABLE_SSL_VERIFICATION)
                    if disable_ssl is not None and disable_ssl:
                        self.disable_ssl_check = True
                        ctx = ssl.create_default_context()
                        ctx.check_hostname = False
                        ctx.verify_mode = ssl.CERT_NONE
                        self.connect_kw_args = {"ssl": ctx}
                    else:
                        self.connect_kw_args = kw_temp
                except JSONDecodeError as jde:
                    raise VantiqConnectorConfigException(f'{VantiqConnector.CONNECT_KW_ARGS} did not contain valid '
                                                         f'JSON string.') from jde


    def __str__(self):
        return f'VantiqSourceConnection for source: {self.source_name}, is_connected: {self.is_connected}'

    def __repr__(self):
        return (f'VantiqSourceConnection(source_name={self.source_name}, config={self.config}, '
                f'reconnectSecret={self._reconnectSecret})')

    def set_connector_set(self, conn_set):
        """ Set the connector set to which this connector belongs

        Parameters:
            conn_set : VantiqConnectorSet
                The VantiqConnectorSet to which this source connection belongs
        """
        self._connector_set = conn_set

    def get_connector_set(self):
        """Fetch the VantiqConnectorSet to which this connection belongs.

        Returns:
            The VantiqConnectorSet to which this connection belongs, None if
            the VantiqconnectorSet is not available
        """

        return self._connector_set

    async def declare_healthy(self) -> None:
        """(Async) Declare the health status of the associated connector set."""

        if self._connector_set is not None:
            await self._connector_set.declare_healthy()

    async def declare_unhealthy(self) -> None:
        """(Async) Declare the health status of the associated connector set."""

        if self._connector_set is not None:
            await self._connector_set.declare_unhealthy()

    def is_healthy(self):
        """Returns the health status of the containing connector set.

       Returns None when no health status has been set;  otherwise, returns a boolean
       indicating whether the connector set is healthy.
       """
        if self._connector_set is not None:
            return self._connector_set.is_healthy()
        else:
            return None

    def get_source(self) -> string:
        """Return the source name with which this connection is associated
        Returns:
            name of the source on whose behalf this class is operating
        """
        return self.source_name

    def get_server_config(self) -> dict:
        """Get the server config information

        Returns:
            Return the server.config file as a dict object
        """
        return self.config

    def configure_handlers(self, handle_close: Union[Callable[[dict], Awaitable[None]],  None],
                           handle_connect: Union[Callable[[dict, dict], Awaitable[None]], None],
                           handle_publish: Union[Callable[[dict, dict], Awaitable[None]], None],
                           handle_query: Union[Callable[[dict, dict], Awaitable[None]], None]) -> None:
        """Provide handlers for close, connect, publish, and query operations

        Parameters:
            handle_close : Callable[[dict], Awaitable[None]]
            handle_connect : Callable[[dict, dict], Awaitable[None]]
            handle_publish : Callable[[dict, dict], Awaitable[None]]
            handle_query : Callable[[dict, dict], Awaitable[None]]
                These parameters specify the callbacks to handle close, connect, publish, and query calls, respectively.
                For each, the first parameter is a dict providing the context for the operation.  The context will
                contain an entry keyed with VantiqConnector.SOURCE_NAME whose value is the name of the source
                for which this is a callback.  For query calls, there will be an additional entry keyed with
                VantiqConnector.RESPONSE_ADDRESS. The value here is used to route the query response back to
                the caller in the Vantiq server.

                This context parameter is to be used in the various query response calls.

                For the handle_connect, handle_query, and handle_publish calls, there will be a second dict named
                parameter. This will contain the message sent from Vantiq to the connector (source).

        """
        self.close_handler = handle_close
        self.connect_handler = handle_connect
        self.publish_handler = handle_publish
        self.query_handler = handle_query

    def _configure_connection(self, file_name) -> dict:
        global _vlog

        server_config = None
        scf = None
        try:
            scf = open(file_name, encoding='utf-8', mode='rt')
            server_config = jprops.load_properties(scf)
        except FileNotFoundError:
            # This means that the file  wasn't there.  Let's look in the other std place.
            _vlog.warning('Failed to open %s', file_name)
        finally:
            if scf is not None:
                scf.close()

        if server_config and VantiqConnector.TARGET_SERVER in server_config.keys():
            # If things are looking reasonable, fetch the authToken if necessary
            if VantiqConnector.AUTH_TOKEN not in server_config.keys():
                # Defined behavior is that serverConfig.authToken overrides env. variable
                auth_tok = os.environ.get(VantiqConnector.CONNECTOR_AUTH_TOKEN)
                server_config[VantiqConnector.AUTH_TOKEN] = auth_tok

            fixed_url = sanitize_url(server_config[VantiqConnector.TARGET_SERVER])
            if fixed_url is not None:
                server_config[VantiqConnector.TARGET_SERVER] = fixed_url

        return server_config
        # serverConfig holds the properties from our server.config file.  These are used to define the server connection

    async def connect_to_vantiq(self):
        """(Async) Make the connection to Vantiq

        This will make the connection to Vantiq and begin dispatching messages to the connector via the defined
        handlers. If successful, this method will not return when awaited.
        """
        global _vlog

        if self.config is None or VantiqConnector.TARGET_SERVER not in self.config.keys():
            raise VantiqConnectorConfigException(f'No valid server.config file was found.  '
                                                 f'Working directory: {os.getcwd()}.')

        _vlog.debug('Using character set: %s', sys.getdefaultencoding())
        do_pings = True
        if VantiqConnector.SEND_PINGS not in self.config.keys() \
                or self.config[VantiqConnector.SEND_PINGS].lower == 'false':
            do_pings = False

        self._is_connected_future = asyncio.get_event_loop().create_future()

        fail_fast = False
        if VantiqConnector.FAIL_ON_CONNECTION_ERROR in self.config.keys():
            fail_fast = self.config[VantiqConnector.FAIL_ON_CONNECTION_ERROR]
        reason = None
        fail_count = 0
        while reason != _TEST_CLOSE:  # reason can be CLOSED in test scenarios only
            _vlog.info('Connector for source %s is connecting to Vantiq at: %s',
                       self.source_name, self.config[VantiqConnector.TARGET_SERVER])
            try:
                reason = await self._perform_connection(do_pings)
                fail_count = 0
            except VantiqConnectorConfigException as vcce:
                # Generally, these are not recoverable and will not be helped by continuous retry.
                raise vcce from None
            except VantiqConnectorException as vce:
                reason = vce
                fail_count += 1
            except RuntimeError as rte:
                fail_count += 1
                reason = rte
            except socket.gaierror as gaierror:
                # This means that the URL is (currently) not valid.
                # 'gai' --> GetAddressInfo
                fail_count += 1
                reason = gaierror
            except OSError as e:
                fail_count += 1
                reason = e
                _vlog.debug('Connection error to %s: %s',
                            self.config[VantiqConnector.TARGET_SERVER], type(reason).__name__)
            except Exception as exc:
                reason = exc
                fail_count += 1
            finally:
                if self.is_connected:
                    # Then we need close the connection & let our caller know.
                    # Then, we'll redo things. The actions we do here are the same regardless.
                    local = self.close_handler
                    if local is not None:
                        await local(self._create_context(None))
                self.is_connected = False
                if fail_fast and fail_count > 0:
                    _vlog.error(f'failOnConnectionError set & could not connect: {reason}')
                    raise reason from None
                # After a disconnect & subsequent reconnect failure, wait a bit to let things settle down.
                if fail_count > 0:
                    wait_period = 0.5 * fail_count
                    _vlog.warning(f'Waiting {wait_period} to reconnect...')
                    await asyncio.sleep(wait_period)
            _vlog.debug('Looping in connector with reason: %s', str(reason))
        _vlog.info('Connector for source %s via Vantiq at: %s is completing',
                   self.source_name, self.config[VantiqConnector.TARGET_SERVER])

    async def _perform_connection(self, do_pings: bool) -> string:
        global _vlog

        try:
            _vlog.debug('perform_connection() to %s', self.config[VantiqConnector.TARGET_SERVER])
            async with websockets.connect(uri=self.config[VantiqConnector.TARGET_SERVER],
                                          ping_interval=20 if do_pings else None,
                                          ping_timeout=20 if do_pings else None,
                                          **self.connect_kw_args) as websocket:
                _vlog.debug('Connection completed')
                auth_msg = {
                    _OPERATION: _OP_VALIDATE,
                    _RESOURCE_NAME: 'system.credentials',
                    _OBJECT: self.config[VantiqConnector.AUTH_TOKEN]
                }

                await websocket.send(json.dumps(auth_msg))
                raw_resp = await websocket.recv()
                resp = json.loads(raw_resp)

                _vlog.debug('Authenticate returned: %s', resp)
                if _STATUS in resp.keys():
                    # then we probably have an error
                    if resp[_STATUS] != 200:
                        _vlog.error('Connect call failed: %s :: %s:%s', resp[_STATUS],
                                    resp[_BODY][0]['code'], resp[_BODY][0]['message'])
                        raise VantiqConnectorConfigException('Connect call failed: {0} :: {1}:{2}'.format(resp[_STATUS],
                                                             resp[_BODY][0]['code'],  resp[_BODY][0]['message']))

                connect_msg = {_OPERATION: _OP_CONNECT_EXTENSION,
                               _RESOURCE_NAME: _SOURCES_RESOURCE,
                               _RESOURCE_ID: self.source_name,
                               _PARAMETERS: {
                                   _PARAM_RECONNECT_SECRET: self._reconnectSecret
                               }}
                await websocket.send(json.dumps(connect_msg))
                raw_resp = await websocket.recv()
                resp = json.loads(raw_resp)

                possible_status_message = 0
                # Sometimes we get a status message back.  If so & it's OK, wait for the config message
                while _OPERATION not in resp.keys() and possible_status_message < 10:
                    possible_status_message += 1
                    if _STATUS in resp.keys():
                        _vlog.debug('Connect returned: %s', resp[_STATUS])
                        status = resp[_STATUS]
                        if status >= 300:
                            return _CONNECTION_FAILED
                    raw_resp = await websocket.recv()
                    resp = json.loads(raw_resp)

                if _OPERATION not in resp.keys():
                    error = f'Unable to make connection. No {_OP_CONFIGURE_EXTENSION} ' \
                                f'message received after {possible_status_message} tries.'
                    _vlog.error(error)
                    raise VantiqConnectorException(error)
                else:
                    # Otherwise, we should have a configExtension message
                    if resp[_OPERATION] == _OP_CONFIGURE_EXTENSION:
                        # Here, we have a configuration that's been returned.  Process it
                        _vlog.debug('Configuration message: %s', resp[_OPERATION])
                        if _OBJECT in resp.keys() and 'config' in resp[_OBJECT].keys():
                            _vlog.debug('Configuration is: %s', resp[_OBJECT]['config'])
                            local = self.connect_handler
                            if local:
                                await local(self._create_context(None), resp[_OBJECT]['config'])
                        else:
                            error = f'Malformed configuration message: {resp}. Please report to Vantiq.'
                            _vlog.error(error)
                            raise VantiqConnectorException(error)
                    else:
                        error = f'Unexpected operation for configuration: {resp[_OPERATION]} -- {resp}. ' \
                            f'Please report to Vantiq'
                        _vlog.error(error)
                        raise VantiqConnectorException(error)
                self.connection = websocket
                self.is_connected = True
                reason = await self._perform_message_processing()
                return reason
        except Exception as e:
            _vlog.error('Failed to connect to server: %s', e)
            raise e from None

    async def _perform_message_processing(self):
        # If we've gotten this far, then we loop processing messages
        try:
            # The future below allows holding off work until the connection is ready
            self._is_connected_future.set_result(f'Connected to src {self.source_name} '
                                                 f'at {self.config[VantiqConnector.TARGET_SERVER]}')

            retval = 'COMPLETED'
            async for rawMessage in self.connection:
                message = json.loads(rawMessage)
                _vlog.debug('VantiqConnector received message: %s', message)
                if 'op' in message.keys():
                    op = message[_OPERATION]
                    if op == _OP_RECONNECT_REQUIRED:
                        retval = _OP_RECONNECT_REQUIRED
                        break
                    elif op == _TEST_CLOSE:
                        retval = _TEST_CLOSE
                        break
                    else:
                        await self._process_message(op, message)
                elif _STATUS in message.keys():
                    # Then this is a http response message.  Accept that if it's OK.
                    if message[_STATUS] >= 300:
                        # Then we have some error condition.  Log it & move on
                        _vlog.error(f'Received status message indicating a problem: {message}')
                else:
                    _vlog.error(f'Malformed message received from server: {message}')
            return retval
        finally:
            old_future = self._is_connected_future
            self._is_connected_future = asyncio.get_event_loop().create_future()
            if old_future is not None and not old_future.done():
                # Mark a cancelled state so that any waiters will find out
                old_future.cancel()
            self.connection = None

    async def close(self):
        """(Async) Close the connection to the Vantiq server."""
        if self.connection is not None:
            await self.connection.close()
            self.connection = None

    async def _process_message(self, op: string, message: dict):
        global _vlog
        global _OP_PUBLISH
        global _OP_QUERY

        local = None
        if op == _OP_PUBLISH:
            local = self.publish_handler
        elif op == _OP_QUERY:
            local = self.query_handler
        else:
            _vlog.error('Unexpected operation: %s -- ignored', op)

        if local is not None:
            try:
                await local(self._create_context(message),
                            message[_OBJECT] if message and _OBJECT in message.keys() else None)
            except Exception as e:  # pylint: disable=broad-except
                _vlog.error(f'Exception {type(e).__name__} thrown by {op} handler while processing message {message}')
                _vlog.error(f'Trace: {traceback.format_exc()}')
        else:
            _vlog.error('No handler found for operation %s', op)

    async def send_query_error(self, ctx: dict, message: dict) -> None:
        """(Async) Respond to a Vantiq query with an error

        Parameters:
            ctx : dict
                The context for the query.  Contains the VantiqConnector.SOURCE_NAME and
                VantiqConnector.RESPONSE_ADDRESS entries.
            message : dict
                The error message to return.  The error message should contain the following entries:
                    VantiqConnector.MESSAGE_CODE --  a string containing a short name for the error
                    VantiqConnector.MESSAGE_TEMPLATE  -- a string describing the problem.  Parameters in this template
                                        are indexed from 0, and indicated using {index}.
                    VanticConnector.PARAMETERS -- a list/array containing the parameters to be substituted
                                        in the template.

        Examples:
        ::
            # Assumes ctx was provided by the handler, and that cnx is the VantiqSourceConnection

            await cnx.send_query_error(ctx, {VantiqConnector.MESSAGE_CODE: 'my.connector.badparameter',
                                       VantiqConnector:MESSAGE_TEMPLATE: 'The parameter {0} with value {1} is invalid',
                                       VantiqConnector.MESSAGE_PARAMETERS: ['some_param_name', some_bad_value]})
        """
        if (ctx is None
                or VantiqConnector.SOURCE_NAME not in ctx
                or ctx[VantiqConnector.SOURCE_NAME] != self.source_name
                or VantiqConnector.RESPONSE_ADDRESS not in ctx
                or ctx[VantiqConnector.RESPONSE_ADDRESS] is None):
            raise VantiqConnectorException(f'send_query_error(): Missing or incomplete context: {ctx}.')
        if (message is None
                or message[VantiqConnector.ERROR_CODE] is None
                or message[VantiqConnector.ERROR_TEMPLATE] is None
                or message[VantiqConnector.ERROR_PARAMETERS] is None):
            raise VantiqConnectorException(f'send_query_error(): Missing or incomplete error message information: '
                                           f'{message}')
        await self._do_message_send(ctx, VantiqConnector.QUERY_ERROR, message)

    async def send_query_response(self, ctx: dict, code: int, message: Union[dict, list, None]):
        """(Async) Respond to a Vantiq query

                Parameters:
                    ctx : dict
                        The context for the query.  Contains the VantiqConnector.SOURCE_NAME and
                        VantiConnector.RESPONSE_ADDRESS entries.
                    code : int
                        Status code to return.  Use VantiqConnector.QUERY_PARTIAL when making several
                        calls to return things. Use VantiqConnector.QUERY_COMPLETE to indicate
                    `   the last of the return calls. Use VantiqConnector.QUERY_EMPTY to return an empty result.
                    message : dict | list | None
                        The message to return.  The entries will comprise the entry to the "row" coming back from the
                        Vantiq VAIL SELECT statement. If a list is passed, it should be a list of dict objects, each
                        representing a row returned to the SELECT statement.

                Examples:
                ::
                    # Assumes ctx was provided by the handler, and that cnx is the VantiqSourceConnection

                    await cnx.send_query_response(ctx, VantiqConnector.QUERY_COMPLETE,
                                                 {'name': 'someName', 'otherProperty': 'some value'})
            """
        if code not in [VantiqConnector.QUERY_EMPTY, VantiqConnector.QUERY_COMPLETE, VantiqConnector.QUERY_PARTIAL]:
            raise VantiqConnectorException(f'send_query_response(): Invalid Code: {code}.')
        if code != VantiqConnector.QUERY_EMPTY and message is None:
            raise VantiqConnectorException('send_query_response(): Non-empty responses require a message parameter.')
        await self._do_message_send(ctx, code, message)

    async def _do_message_send(self, ctx: dict, code: int, message):
        if (ctx is None
                or VantiqConnector.SOURCE_NAME not in ctx
                or ctx[VantiqConnector.SOURCE_NAME] != self.source_name
                or VantiqConnector.RESPONSE_ADDRESS not in ctx
                or ctx[VantiqConnector.RESPONSE_ADDRESS] is None):
            raise VantiqConnectorException(f'Query response is missing or has incomplete context: {ctx}.')

        msg_to_send = {_STATUS: code, 'headers': {_RESPONSE_ADDRESS_HEADER: ctx[VantiqConnector.RESPONSE_ADDRESS]}}
        if code != VantiqConnector.QUERY_EMPTY:
            msg_to_send[_BODY] = message

        raw_msg = json.dumps(msg_to_send)
        ready = False
        is_ready_future = self._is_connected_future
        if is_ready_future is None:
            raise VantiqConnectorException('Connector has no ready future.')
        while not ready:
            await is_ready_future
            if is_ready_future.done():
                # If the future finished but no successfully, get a new future & rewait
                if is_ready_future.exception() is not None or is_ready_future.cancelled():
                    # The system will reconnect here, replacing the future in the process.  Simply retry
                    is_ready_future = self._is_connected_future
                else:
                    ready = True
        try:
            await self.connection.send(raw_msg)
        except Exception as e:
            _vlog.error(f'Trapped exception during sending response for source {self.source_name}: {type(e).__name__}')
            raise e from None

    async def send_notification(self, message: dict):
        """(Async) Send a notification (an event) to the Vantiq server.

        Parameters:
            message : dict
                The event value to send.  The event will appear as an event from this source.
        """
        msg_to_send = {_OPERATION: _OP_NOTIFY,
                       _RESOURCE_NAME: _SOURCES_RESOURCE,
                       _RESOURCE_ID: self.source_name,
                       _OBJECT: message}
        raw_msg = json.dumps(msg_to_send)
        ready = False
        is_ready_future = self._is_connected_future
        if is_ready_future is None:
            raise VantiqConnectorException('Connector has no ready future.')
        while not ready:
            await is_ready_future
            if is_ready_future.done():
                # If the future finished but no successfully, get a new future & rewait
                if is_ready_future.exception() is not None or is_ready_future.cancelled():
                    # The system will reconnect here, replacing the future in the process.  Simply retry
                    is_ready_future = self._is_connected_future
                else:
                    ready = True
        try:
            await self.connection.send(raw_msg)
        except Exception as e:
            _vlog.error('Trapped exception during send for source %s: %s', self.source_name, type(e).__name__)
            raise e from None

    def _create_context(self, message: Union[dict, None]):
        ctx: dict = {VantiqConnector.SOURCE_NAME: self.source_name}
        if (message is not None
                and _MESSAGE_HEADERS in message
                and message[_MESSAGE_HEADERS] is not None
                and _ORIGIN_ADDRESS in message[_MESSAGE_HEADERS]
                and message[_MESSAGE_HEADERS][_ORIGIN_ADDRESS] is not None):
            ctx[VantiqConnector.RESPONSE_ADDRESS] = message[_MESSAGE_HEADERS][_ORIGIN_ADDRESS]
        return ctx


class VantiqConnectorSet:
    """This is set of VantiqSourceConnection managed by this process. It includes the management interface
    for this set of VantiqSourceConnection.

    The VantiqConnectorSet takes its input from the serverConfig/server.config file, and manages the creation
    and operation of these connections.
    """

    def __init__(self):
        self._sources = []
        self._connections = {}
        self._health_control_lock = asyncio.Lock()
        self.socket_server = None
        self.healthy = None  # Start om undeclared state.
        self._server_config: Union[dict, None] = None
        self._read_configuration()

        # Initialize the set of connections for the caller
        for src in self._sources:
            self._connections[src] = VantiqSourceConnection(src, self._server_config)
            self._connections[src].set_connector_set(self)

    def __str__(self):
        ret_val = 'VantiqConnectorSet for '
        for src in self._sources:
            ret_val += '\n\t' + str(self._connections[src])

    def __repr__(self):
        return 'VantiqConnectorSet()'

    def get_logger(self) -> Union[Logger, None]:
        """Returns the logger in use"""
        global _vlog
        return _vlog

    def get_sources(self):
        """Returns the list of sources in this connector set"""
        return self._sources

    def get_connections(self) -> dict:
        """
        This returns the set of VantiqSourceConnections, indexed by source name.

        Returns:
             dict where the item key is the source name, and the item value is a VantiqSourceConnection.
        """
        return self._connections

    def get_connection_for_source(self, source_name: str) -> VantiqSourceConnection:
        """Returns the VantiqSourceConnection for a named source

        Parameters:
            source_name : str
                name of the source for which you are looking.

        Returns:
            VantiqSourceConnection for the named source

            Returns None if there is no connection to the named source

        """
        return self._connections[source_name] if source_name in self._connections else None

    def _read_configuration(self):
        sc = self._read_config_from_file('serverConfig/server.config')
        if sc is None:
            sc = self._read_config_from_file('server.config')

        if sc is None:
            raise VantiqConnectorConfigException('No server.config file found.')

        self._server_config = sc
        self._sources = sc[VantiqConnector.SOURCES].replace(' ', '').split(',')

    def _read_config_from_file(self, file_name) -> Union[dict, None]:
        server_config = None
        scf = None
        try:
            scf = open(file_name, encoding='utf-8', mode='rt')
            server_config = jprops.load_properties(scf)
        except FileNotFoundError:
            # This means that the file  wasn't there.  Let's look in the other std place.
            _vlog.warning('Failed to open %s', file_name)
        finally:
            if scf is not None:
                scf.close()

        if server_config and VantiqConnector.TARGET_SERVER in server_config.keys():
            # If things are looking reasonable, fetch the authToken if necessary
            if VantiqConnector.AUTH_TOKEN not in server_config.keys():
                # Defined behavior is that serverConfig.authToken overrides env. variable
                auth_tok = os.environ.get(VantiqConnector.CONNECTOR_AUTH_TOKEN)
                server_config[VantiqConnector.AUTH_TOKEN] = auth_tok

            fixed_url = sanitize_url(server_config[VantiqConnector.TARGET_SERVER])
            if fixed_url is not None:
                server_config[VantiqConnector.TARGET_SERVER] = fixed_url

        return server_config

    def configure_handlers_for_all(self, handle_close: Union[Callable[[dict], Awaitable[None]], None],
                                   handle_connect: Union[Callable[[dict, dict], Awaitable[None]], None],
                                   handle_publish: Union[Callable[[dict, dict], Awaitable[None]], None],
                                   handle_query: Union[Callable[[dict, dict], Awaitable[None]], None]) -> None:
        """Provide handlers for close, connect, publish, and query operations for all sources in this set

         Parameters:
             handle_close : Callable[[dict], Awaitable[None]]
             handle_connect : Callable[[dict, dict], Awaitable[None]]
             handle_publish : Callable[[dict, dict], Awaitable[None]]
             handle_query : Callable[[dict, dict], Awaitable[None]]
                 These parameters specify callbacks to handle close, connect, publish, and query calls, respectively.
                 For each, the first parameter is a dict providing the context for the operation.  The context will
                 contain an entry keyed with VantiqConnector.SOURCE_NAME whose value is the name of the source
                 for which this is a callback.  For query calls, there will be an additional entry keyed with
                 VantiqConnector.RESPONSE_ADDRESS. The value here is used to route the query response back
                 to the caller in the Vantiq server.

                 This context parameter is to be used in the various query response calls.

                 For handle_connect, handle_query, and handle_publish calls, there will be a second dict parameter.
                 This will contain the message sent from Vantiq to the connector (source).
         """

        for src in self._sources:
            conn: VantiqSourceConnection = self._connections[src]
            conn.configure_handlers(handle_close, handle_connect, handle_publish, handle_query)

    async def _tcp_handler(self, reader, writer):
        """This handler handles messages for the Kubernetes probe.

        There are no messages sent here -- the Kubernetes TPC probe is successful if the connection
        can be opened. But a handler is required, so here it is.

        Parameters:
            reader, writer : StreamReader, StreamWriter
                Reader & writer for the socket.  Not currently used since we only care about opening the socket.
        """
        return

    async def declare_healthy(self):
        """(Async) Declare that this connector is healthy.

        Declaring that the server is healthy sets up a responder for TCP health checks such as those performed
        by Kubernetes.  The connector is also marked as healthy so that any callers can ask about the current state.
        """

        self.healthy = True
        async with self._health_control_lock:
            if self.socket_server is None:
                port: int = VantiqConnector.TCP_PROBE_PORT_DEFAULT
                if VantiqConnector.PORT_PROPERTY_NAME in self._server_config.keys():
                    port = self._server_config[VantiqConnector.PORT_PROPERTY_NAME]
                self.socket_server = await asyncio.start_server(self._tcp_handler, host='', port=port)

    async def declare_unhealthy(self):
        """(Async) Declares that this connector is not healthy.

        Change the health state of the server.  If a health check responder has been set up, cancel it.
        Once cancelled (until restarted via declare_healthy() call), Kubernetes health checks will fail.
        """
        self.healthy = False
        async with self._health_control_lock:
            _vlog.warning('Connector is unhealthy')
            if self.socket_server is not None:
                _vlog.warning('Connector is unhealthy, disabling health probe')
                self.socket_server.close()
                await self.socket_server.wait_closed()
                self.socket_server = None

    def is_healthy(self):
        """Returns the health status of this connector set.

        Returns None when no health status has been set;  otherwise, returns a boolean
        indicating whether the connector set is healthy.
        """
        return self.healthy

    async def run_connectors(self) -> None:
        """(Async) Make the connection(s) to Vantiq

        This will make the connection to Vantiq for all sources in the server.config file and begin dispatching
        messages to the connector via the defined handlers. If successful, this method will not return when awaited.
        """

        connector_calls = []
        for src in self._sources:
            connector_calls.append(self._connections[src].connect_to_vantiq())
        _vlog.info('Starting %i connectors', len(connector_calls))
        await asyncio.gather(*connector_calls)

    async def close(self) -> None:
        """(Async) Close the connection to Vantiq.

        This will close the connections for all sources in this set.
        """
        for src in self._sources:
            await self._connections[src].close()
        if self.socket_server:
            self.socket_server.close()
            await self.socket_server.wait_closed()

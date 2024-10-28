

__author__ = 'fhcarter'
__copyright__ = "Copyright 2022, Vantiq, Inc."
__license__ = "MIT License"
__email__ = "support@vantiq.com"

import asyncio
import json
import re
import os
import string

import pytest
import websockets.exceptions

from testserver import run_server
# noinspection PyProtectedMember
from vantiqconnectorsdk import VantiqConnectorSet, VantiqConnector, setup_logging, _WEBSOCKET_URL_PATTERN

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

if not os.path.exists('serverConfig'):
    os.makedirs('serverConfig')

lg = open('serverConfig/logger.ini', mode='wt')
lg.write(loginit)
lg.close()


async def run_server_test(port, config, pub_count, note_count, connector: VantiqConnectorSet, server_disc_count):
    tasks = []
    loop = asyncio.get_event_loop()
    tasks.append(loop.create_task(run_server(port, config, pub_count, note_count, server_disc_count)))
    tasks.append(loop.create_task(run_client_test(note_count, connector)))
    await asyncio.gather(*tasks)
    for t in tasks:
        t.cancel()


async def run_client_test(note_count, conn_set: VantiqConnectorSet):
    conn = asyncio.create_task(conn_set.run_connectors())
    tasks = [conn]

    for name, a_conn in conn_set.get_connections().items():
        print('Adding task for sending {0} notifications to source {1}'.format(note_count, name))
        a_sender = do_notifications(a_conn, note_count)
        tasks.append(a_sender)

    await asyncio.gather(*tasks)


async def do_notifications(connector, note_count):
    if note_count > 0:
        incomplete_count = 0
        while connector.connection is None and incomplete_count < 500:
            incomplete_count += 1
            print('Connection is not complete: ({0} times) for note_count: {1}'.format(incomplete_count, note_count))
            await asyncio.sleep(0.1)
        assert connector.connection
        sent_count = 0
        while sent_count < note_count:
            # sent_count facilitates retry when exceptions occur during the send_notification() call
            # This is expected to happen during our disconnect-type tests
            for i in range(note_count):
                try:
                    print('Sending notification # ', i)
                    await connector.send_notification({"hi": "mom", "msgCount": i})
                    sent_count += 1
                    # Inject these very brief "yields" to get a better intermixed behavior for the tests
                    # Due to the coroutine nature of asyncio, without them, we tend to get all the notifies
                    # then all the publishes.  That still happens sometimes, but these very slight pauses
                    # allow other parts of the test to operate.  This more thoroughly exercises the SDK.
                    await asyncio.sleep(0.01)
                except RuntimeError as r_e:
                    print('Failed to send message number {0} due to {1}'.format(i, r_e))
                    assert r_e is None   # This will never be true here but will show us the error
                except websockets.exceptions.ConnectionClosed:
                    # Means that our test closed the connecting out from under us.  Ignore
                    await asyncio.sleep(0)  # Yield CPU so that server can start back up...
                    pass
        print('do_notifications() has completed.')


class TestSingleConnection:

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

    @classmethod
    def teardown_class(cls):
        os.remove('serverConfig/logger.ini')
        os.rmdir('serverConfig')

    def setup_method(self):
        """This resets things before each test"""
        self._close_count = 0
        self._connect_count = 0
        self._publish_count = 0
        self._query_count = 0
        self._is_connected = False

    def test_configuration(self):
        source_name = 'pythonTestSource'
        cf = f'''
        targetServer=http://localhost:9090
        authToken=testtoken
        sources={source_name}
                    '''
        filename = None
        try:
            scf = open('serverConfig/server.config', encoding='utf-8', mode='wt')
            scf.write(cf)
            filename = scf.name
            scf.close()
            vc = VantiqConnectorSet()
            connector = vc.get_connection_for_source(source_name)
            source = connector.get_source()
            assert source == source_name
            sc = connector.get_server_config()
            assert sc
            assert sc[VantiqConnector.TARGET_SERVER]
            assert sc[VantiqConnector.AUTH_TOKEN]
            assert sc[VantiqConnector.TARGET_SERVER].startswith('ws')
            assert VantiqConnector.CONNECT_KW_ARGS not in sc
            assert re.match(_WEBSOCKET_URL_PATTERN, sc[VantiqConnector.TARGET_SERVER])
            assert self._close_count == 0
            assert self._connect_count == 0
            assert self._publish_count == 0
            assert self._query_count == 0
        finally:
            if filename:
                os.remove(filename)

    def test_configuration_with_connect_args(self):
        source_name = 'pythonTestSource'
        # Note: 'false' must be JSON format, not False of Python...
        connect_args = '{"ssl": false, "server_hostname": "host.not.there.com" }'
        cf = f'''
        targetServer=http://localhost:9090
        authToken=testtoken
        sources={source_name}
        connectKWArgs={connect_args}
                    '''
        filename = None
        try:
            scf = open('serverConfig/server.config', encoding='utf-8', mode='wt')
            scf.write(cf)
            filename = scf.name
            scf.close()
            vc = VantiqConnectorSet()
            connector = vc.get_connection_for_source(source_name)
            source = connector.get_source()
            assert source == source_name
            sc = connector.get_server_config()
            assert sc
            assert sc[VantiqConnector.TARGET_SERVER]
            assert sc[VantiqConnector.AUTH_TOKEN]
            assert sc[VantiqConnector.TARGET_SERVER].startswith('ws')
            assert sc[VantiqConnector.CONNECT_KW_ARGS] is not None
            assert sc[VantiqConnector.CONNECT_KW_ARGS]
            # Ensure that the connection arguments in the config file where populated as expected
            da : dict = connector.connect_kw_args
            assert da['ssl'] is not None
            assert da['server_hostname'] is not None
            assert da['server_hostname'] == 'host.not.there.com'
            assert re.match(_WEBSOCKET_URL_PATTERN, sc[VantiqConnector.TARGET_SERVER])
            assert self._close_count == 0
            assert self._connect_count == 0
            assert self._publish_count == 0
            assert self._query_count == 0
        finally:
            if filename:
                os.remove(filename)

    @pytest.mark.asyncio
    @pytest.mark.timeout(20)
    async def test_only_publish(self, unused_tcp_port):
        # Construct a server config file to use...
        source_name = 'pythonTestSource'
        cf = f'''
targetServer=http://localhost:{unused_tcp_port}
authToken=testtoken
sources={source_name}
            '''
        try:
            scf = open('server.config', encoding='utf-8', mode='wt')
            scf.write(cf)
            filename = scf.name
            scf.close()
            vc = VantiqConnectorSet()
            vc.configure_handlers_for_all(self.close_handler, self.connect_handler,
                                          self.publish_handler, self.query_handler)
            expected_message_count = 125
            await run_server_test(unused_tcp_port, filename, expected_message_count, 0, vc, 0)
            assert self._message_count == expected_message_count
            assert self._connect_count == 1
            assert self._close_count == self._connect_count
            assert self._publish_count == expected_message_count
            assert self._query_count == 0

        finally:
            os.remove('server.config')

    @pytest.mark.asyncio
    @pytest.mark.timeout(20)
    async def test_only_publish_with_connect_args(self, unused_tcp_port):
        # Construct a server config file to use...
        source_name = 'pythonTestSource'
        # Note: 'max_queue' is not of interest, it's just harmless,
        # Sending, e.g., ssl=false fails because the connection is not a secure (ssl) connection
        # Here, we're just validating that we can send something through successfully
        connect_args = '{ "max_queue" : 16 }'
        cf = f'''
    targetServer=http://localhost:{unused_tcp_port}
    authToken=testtoken
    sources={source_name}
    connectKWArgs={connect_args}
                '''
        try:
            scf = open('server.config', encoding='utf-8', mode='wt')
            scf.write(cf)
            filename = scf.name
            scf.close()
            vc = VantiqConnectorSet()
            vc.configure_handlers_for_all(self.close_handler, self.connect_handler,
                                          self.publish_handler, self.query_handler)
            expected_message_count = 125
            await run_server_test(unused_tcp_port, filename, expected_message_count, 0, vc, 0)
            assert self._message_count == expected_message_count
            assert self._connect_count == 1
            assert self._close_count == self._connect_count
            assert self._publish_count == expected_message_count
            assert self._query_count == 0

        finally:
            os.remove('server.config')

    @pytest.mark.asyncio
    @pytest.mark.timeout(20)
    async def test_publish_and_notify(self, unused_tcp_port):
        # Construct a server config file to use...
        source_name = 'pythonTestSource'
        cf = f'''
targetServer=http://localhost:{unused_tcp_port}
authToken=testtoken
sources={source_name}
            '''
        try:
            scf = open('server.config', encoding='utf-8', mode='wt')
            scf.write(cf)
            filename = scf.name
            scf.close()
            vc = VantiqConnectorSet()
            vc.configure_handlers_for_all(self.close_handler, self.connect_handler,
                                          self.publish_handler, self.query_handler)
            expected_message_count = 125
            await run_server_test(unused_tcp_port, filename, expected_message_count, 27, vc, 0)
            assert self._message_count == expected_message_count
            assert self._connect_count == 1
            assert self._close_count == self._connect_count
            assert self._publish_count == expected_message_count
            assert self._query_count == 0

        finally:
            os.remove('server.config')

    @pytest.mark.asyncio
    @pytest.mark.timeout(20)
    async def test_server_reconnect(self, unused_tcp_port):
        # Construct a server config file to use...
        source_name = 'pythonTestSource'
        cf = f'''
targetServer=http://localhost:{unused_tcp_port}
authToken=testtoken
sources={source_name}
            '''
        try:
            scf = open('server.config', encoding='utf-8', mode='wt')
            scf.write(cf)
            filename = scf.name
            scf.close()
            vc = VantiqConnectorSet()
            vc.configure_handlers_for_all(self.close_handler, self.connect_handler,
                                          self.publish_handler, self.query_handler)
            expected_message_count = 125
            reconnect_on_multiple = 10
            await run_server_test(unused_tcp_port, filename, expected_message_count, 27, vc, reconnect_on_multiple)
            assert self._message_count == expected_message_count
            assert self._connect_count == 1 + (expected_message_count // reconnect_on_multiple)
            assert self._close_count == self._connect_count
            assert self._publish_count == expected_message_count
            assert self._query_count == 0

        finally:
            os.remove('server.config')

    @pytest.mark.asyncio
    @pytest.mark.timeout(20)
    async def test_server_healthcheck(self, unused_tcp_port_factory):
        # Construct a server config file to use.
        server_port = unused_tcp_port_factory()
        healthcheck_port = unused_tcp_port_factory()

        source_name = 'pythonTestSource'
        cf = f'''
targetServer=http://localhost:{server_port}
authToken=testtoken
sources={source_name}
{VantiqConnector.PORT_PROPERTY_NAME} = {healthcheck_port}
            '''
        try:
            scf = open('server.config', encoding='utf-8', mode='wt')
            scf.write(cf)
            filename = scf.name
            scf.close()
            vc = VantiqConnectorSet()
            vc.configure_handlers_for_all(self.close_handler, self.connect_handler,
                                          self.publish_handler, self.query_handler)

            connector_task = asyncio.get_event_loop().create_task(vc.run_connectors())
            await asyncio.sleep(0.5)
            got_connect_exception = False
            assert vc.is_healthy() is None  # No health status declared yet
            try:
                reader, writer = await asyncio.open_connection(None,
                                                               vc._server_config[VantiqConnector.PORT_PROPERTY_NAME])
                assert reader is None
                assert writer is None
            except OSError as ose:
                # This is what we want
                got_connect_exception = True

            except Exception as e:
                assert e is None

            assert got_connect_exception
            got_connect_exception = False
            await vc.declare_healthy()
            assert vc.is_healthy()
            try:
                reader, writer = await asyncio.open_connection(None,
                                                               vc._server_config[VantiqConnector.PORT_PROPERTY_NAME])
                assert reader is not None
                assert writer is not None
            except Exception as e:
                got_connect_exception = True
                assert e is None

            assert not got_connect_exception
            got_connect_exception = False
            await vc.declare_unhealthy()
            assert not vc.is_healthy()
            try:
                reader, writer = await asyncio.open_connection(None,
                                                               vc._server_config[VantiqConnector.PORT_PROPERTY_NAME])
                assert reader is None
                assert writer is None
            except OSError as ose:
                # This is what we want
                got_connect_exception = True
            except Exception as e:
                assert e is None
            assert got_connect_exception
            await vc.close()
            connector_task.cancel()
            while not connector_task.done():
                await asyncio.sleep(0.1)
        finally:
            os.remove('server.config')

    @pytest.mark.asyncio
    @pytest.mark.timeout(20)
    async def test_source_healthcheck(self, unused_tcp_port_factory):
        # Construct a server config file to use...
        source_name = 'pythonTestSource'
        # Separated these from being inline with the file content. Seemed to be some sync issues
        # when they were embedded in the file content.  Separately, they seem more reliable.
        server_port = unused_tcp_port_factory()
        healthcheck_port = unused_tcp_port_factory()
        cf = f'''
targetServer=http://localhost:{server_port}
authToken=testtoken
sources={source_name}
{VantiqConnector.PORT_PROPERTY_NAME} = {healthcheck_port}
            '''
        try:
            scf = open('server.config', encoding='utf-8', mode='wt')
            scf.write(cf)
            filename = scf.name
            scf.close()
            vc = VantiqConnectorSet()
            vc.configure_handlers_for_all(self.close_handler, self.connect_handler,
                                          self.publish_handler, self.query_handler)

            connector_task = asyncio.get_event_loop().create_task(vc.run_connectors())
            await asyncio.sleep(0.5)
            got_connect_exception = False
            assert vc.get_connection_for_source(source_name).is_healthy() is None
            try:
                reader, writer = await asyncio.open_connection(None,
                                                               vc._server_config[VantiqConnector.PORT_PROPERTY_NAME])
                assert reader is None
                assert writer is None
            except OSError as ose:
                # This is what we want
                got_connect_exception = True
            except Exception as e:
                assert e is None

            assert got_connect_exception
            got_connect_exception = False
            await vc.get_connection_for_source(source_name).declare_healthy()
            assert vc.get_connection_for_source(source_name).is_healthy()
            try:
                reader, writer = await asyncio.open_connection(None,
                                                               vc._server_config[VantiqConnector.PORT_PROPERTY_NAME])

                assert reader is not None
            except Exception as e:
                got_connect_exception = True
                assert e is None
            assert not got_connect_exception
            got_connect_exception = False
            await vc.get_connection_for_source(source_name).declare_unhealthy()
            assert not vc.get_connection_for_source(source_name).is_healthy()
            try:
                reader, writer = await asyncio.open_connection(None,
                                                               vc._server_config[VantiqConnector.PORT_PROPERTY_NAME])
                assert reader is None
                assert writer is None
            except OSError as ose:
                # This is what we want
                got_connect_exception = True
            except Exception as e:
                assert e is None
            assert got_connect_exception
            await vc.close()
            connector_task.cancel()
            while not connector_task.done():
                await asyncio.sleep(0.1)
        finally:
            os.remove('server.config')

    @pytest.mark.asyncio
    @pytest.mark.timeout(20)
    async def test_server_healthcheck_default_port(self, unused_tcp_port_factory):
        # Construct a server config file to use...
        source_name = 'pythonTestSource'
        server_port = unused_tcp_port_factory()
        cf = f'''
targetServer=http://localhost:{server_port}
authToken=testtoken
sources={source_name}
            '''
        try:
            scf = open('server.config', encoding='utf-8', mode='wt')
            scf.write(cf)
            filename = scf.name
            scf.close()
            vc = VantiqConnectorSet()
            vc.configure_handlers_for_all(self.close_handler, self.connect_handler,
                                          self.publish_handler, self.query_handler)

            connector_task = asyncio.get_event_loop().create_task(vc.run_connectors())
            await asyncio.sleep(0.5)
            got_connect_exception = False
            try:
                reader, writer = await asyncio.open_connection(None, VantiqConnector.TCP_PROBE_PORT_DEFAULT)
                assert reader is None
                assert writer is None
            except OSError as ose:
                # This is what we want
                got_connect_exception = True
            except Exception as e:
                assert e is None

            assert got_connect_exception
            got_connect_exception = False
            await vc.declare_healthy()
            assert vc.is_healthy()
            try:
                reader, writer = await asyncio.open_connection(None, VantiqConnector.TCP_PROBE_PORT_DEFAULT)
                assert reader is not None
                assert writer is not None
            except Exception as e:
                got_connect_exception = True
                assert e is None

            assert not got_connect_exception
            got_connect_exception = False
            await vc.declare_unhealthy()
            assert not vc.is_healthy()
            try:
                reader, writer = await asyncio.open_connection(None, VantiqConnector.TCP_PROBE_PORT_DEFAULT)
                assert reader is None
                assert writer is None
            except OSError as ose:
                # This is what we want
                got_connect_exception = True
            except Exception as e:
                assert e is None
            assert got_connect_exception
            await vc.close()
            connector_task.cancel()
            while not connector_task.done():
                await asyncio.sleep(0.1)
        finally:
            os.remove('server.config')

    def check_context(self, ctx: dict):
        assert ctx
        assert ctx[VantiqConnector.SOURCE_NAME]

    async def close_handler(self, ctx: dict):
        self.check_context(ctx)
        assert self._message_count > 0
        assert self._is_connected
        self._is_connected = False
        self._close_count += 1
        # Connects & closes should mirror one another
        assert self._close_count == self._connect_count

    async def connect_handler(self, ctx: dict, message: dict):
        self.check_context(ctx)

        assert 'someProp' in message
        assert message['someProp'] == 'message content'
        assert not self._is_connected
        self._is_connected = True
        self._connect_count += 1
        # Connects & closes should mirror one another, so connect should be one ahead of close
        assert self._connect_count == self._close_count + 1

    async def publish_handler(self, ctx: dict, message: dict):
        self.check_context(ctx)
        assert message
        assert self._is_connected
        self._message_count += 1
        self._publish_count += 1

    async def query_handler(self, ctx: dict, message: dict):
        self.check_context(ctx)
        assert message
        assert self._is_connected
        self._message_count += 1

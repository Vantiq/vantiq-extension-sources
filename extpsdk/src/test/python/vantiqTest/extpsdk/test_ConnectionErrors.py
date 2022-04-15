
import pytest
from vantiq.extpsdk.VantiqConnector import VantiqSourceConnection, VantiqConnectorSet, VantiqConnectorConfigException
from vantiq.extpsdk import VantiqConnector
import asyncio
from .testserver import run_server
import re
import os

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


async def run_server_test(port, config, pub_count, connector: VantiqSourceConnection):
    tasks = []
    try:
        loop = asyncio.get_event_loop()
        if port > 0:
            tasks.append(loop.create_task(run_server(port, config, pub_count)))
        tasks.append(loop.create_task(connector.connect_to_vantiq()))
        await asyncio.gather(*tasks, return_exceptions=False)
        pass
    finally:
        stopped_tasks =[]
        for t in tasks:
            t.cancel()
            stopped_tasks.append(t)
        # To avoid spurious errors in the log(s), wait for these tasks to complete.
        await asyncio.gather(*stopped_tasks)


class TestFaultyConnections:

    @classmethod
    def setup_class(cls):
        try:
            VantiqConnector.setup_logging()
        except:
            pass

    @pytest.fixture(autouse=True)
    def _setup(self):
        self._message_count = 0
        self._close_count = 0
        self._connect_count = 0
        self._publish_count = 0
        self._query_count = 0
        self._is_connected = False

    def setup_method(self):
        self._close_count = 0
        self._connect_count = 0
        self._publish_count = 0
        self._query_count = 0

    def test_configuration(self):
        with pytest.raises(RuntimeError):
            VantiqConnectorSet()

        source_name = 'pythonTestSource'
        cf = f'''
        targetServer=http://localhost:9090
        authToken=testtoken
        sources={source_name}
                    '''
        filename = None
        try:
            scf = open('server.config', encoding='utf-8', mode='wt')
            scf.write(cf)
            filename = scf.name
            scf.close()

            vc = VantiqConnectorSet()
            sources = vc.get_sources()
            assert len(sources) == 1
            assert sources == [source_name]
            connector = vc.get_connection_for_source(source_name)
            source = connector.get_source()
            assert source == source_name
            sc = connector.get_server_config()
            assert sc is not None
            assert sc[VantiqConnector.TARGET_SERVER] is not None
            assert sc[VantiqConnector.AUTH_TOKEN] is not None
            assert sc[VantiqConnector.TARGET_SERVER].startswith('ws')
            assert re.match(VantiqConnector._WEBSOCKET_URL_PATTERN, sc[VantiqConnector.TARGET_SERVER])
        finally:
            if filename is not None:
                os.remove(filename)

    @pytest.mark.asyncio
    @pytest.mark.timeout(10)
    async def test_connect_bad_auth(self, unused_tcp_port):
        # Construct a server config file to use...
        source_name = 'pythonSource'
        cf = f'''
targetServer=http://localhost:{unused_tcp_port}
authToken=junk
sources={source_name}
'''

        correct_config_in_wrong_place = f'''
targetServer=http://localhost:{unused_tcp_port}
authToken=testtoken
sources={source_name}
'''

        try:
            scf = open('server.config', encoding='utf-8', mode='wt')
            scf.write(cf)
            scf.close()

            scf = open('badserver.config', encoding='utf-8', mode='wt')
            scf.write(correct_config_in_wrong_place)
            filename = scf.name
            scf.close()
            vc = VantiqConnectorSet()
            vc.configure_handlers_for_all(self.close_handler, self.connect_handler,
                                          self.publish_handler, self.query_handler)
            conn = vc.get_connection_for_source(source_name)
            with pytest.raises(VantiqConnectorConfigException) as exc:
                await run_server_test(unused_tcp_port, filename, 25, conn)

            assert exc.value.args[0] == 'Connect call failed: 401 :: authFailure:invalid authToken'
        finally:
            os.remove('server.config')
            os.remove('badserver.config')

    @pytest.mark.asyncio
    @pytest.mark.timeout(10)
    async def test_connect_bad_url(self):
        # Construct a server config file to use...
        source_name = 'pythonSource'
        cf = f'''
targetServer=http://server.does.not.exist:9999
authToken=junk
sources={source_name}
failOnConnectionError=true
'''
        try:
            scf = open('server.config', encoding='utf-8', mode='wt')
            scf.write(cf)
            scf.close()

            vc = VantiqConnectorSet()
            vc.configure_handlers_for_all(self.close_handler, self.connect_handler,
                                          self.publish_handler, self.query_handler)
            conn = vc.get_connection_for_source(source_name)
            with pytest.raises(OSError) as exc:
                # For this case, we don't need a server so don't bother to start one
                await run_server_test(0, None, 25, conn)
            # [Errno 8] nodename nor servname provided, or not known
            assert len(exc.value.args) == 2
            # Actual errors returned vary from OS & version to OS & version.  Can't really test much
            # here except that we got an error.
        finally:
            os.remove('server.config')

    async def close_handler(self):
        assert self._is_connected
        self._is_connected = False
        self._close_count += 1

    async def connect_handler(self, message: dict):
        assert message is not None
        assert 'someProp' in message
        assert message['someProp'] == 'message content'
        assert not self._is_connected
        self._is_connected = True
        self._connect_count += 1

    async def publish_handler(self, message: dict):
        assert message is not None
        assert self._is_connected
        self._message_count += 1
        self._publish_count += 1

    async def query_handler(self, message: dict):
        assert self._is_connected
        assert message is not None
        self._message_count += 1
        self._query_count += 1

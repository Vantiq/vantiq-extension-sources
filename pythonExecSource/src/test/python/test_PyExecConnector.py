__author__ = 'fhcarter'
__copyright__ = "Copyright 2022, Vantiq, Inc."
__license__ = "MIT License"
__email__ = "support@vantiq.com"


import asyncio
from datetime import datetime
import json
import os
import traceback

from aioresponses import aioresponses
import pytest

import testserver
from testserver import run_server, get_results, DOC_NAME, DOC_CONTENTS
from vantiqconnectorsdk import VantiqSourceConnection, setup_logging
from pyExecConnector import Connectors

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

py_code_with_notifies = [
"""
import os
import asyncio

some_list = []
three = 3
somevar = {'message': 'from Python'}
where_are_we = os.getcwd()

async def looper(count):
    global some_list
    for i in range(count):
         some_list.append('I am list item {0}'.format(i))

def main():
    global connector_connection
    print('Looper -- in main')
    loop = asyncio.get_event_loop()
    tasks = []
    tasks.append(loop.create_task(looper(25)))
    print('Looper -- about to wait for the loop')
    asyncio.gather(*tasks)
    print('Looper -- done with wait')
    asyncio.gather(
        loop.create_task(
            connector_connection.send_notification({'notify_msg': 'consider yourself notified -- set 1'})
        )
    )

    
if __name__ == '__main__':
    main()
""",
"""
import os
import asyncio

some_list = []
three = 3
somevar = {'message': 'from Python'}
where_are_we = os.getcwd()

async def looper(count):
    global some_list
    global connector_connection
    for k in range(count):
         some_list.append('I am list item {0}'.format(k))
    await connector_connection.send_notification({'notify_msg': 'consider yourself notified -- set 2'})

def main():
    print('Looper -- in main')
    loop = asyncio.get_event_loop()
    tasks = []
    tasks.append(loop.create_task(looper(25)))
    print('Looper -- about to wait for the loop')
    asyncio.gather(*tasks)
    print('Looper -- done with wait')

    
if __name__ == '__main__':
    main()
""",
"""
import os
import asyncio

some_list = []
three = 3
somevar = {'message': 'from Python'}
where_are_we = os.getcwd()

async def looper(count):
    global some_list
    global connector_connection
    await connector_connection.send_notification({'notify_msg': 'consider yourself notified -- set 3'})
    for m in range(count):
         some_list.append('I am list item {0}'.format(m))

def main():

    print('Looper -- in main')
    loop = asyncio.get_event_loop()
    tasks = []
    tasks.append(loop.create_task(looper(25)))
    print('Looper -- about to wait for the loop')
    asyncio.gather(*tasks)
    print('Looper -- done with wait')

    
if __name__ == '__main__':
    main()
"""
]

py_docs_with_notifies = [
    {DOC_NAME: 'pynotify1.py', DOC_CONTENTS:
"""
import os
import asyncio

some_list = []
three = 3
somevar = {'message': 'from Python'}
where_are_we = os.getcwd()

async def looper(count):
    global some_list
    for i in range(count):
         some_list.append('I am list item {0}'.format(i))

def main():
    global connector_connection
    print('Looper -- in main')
    loop = asyncio.get_event_loop()
    tasks = []
    tasks.append(loop.create_task(looper(25)))
    print('Looper -- about to wait for the loop')
    asyncio.gather(*tasks)
    print('Looper -- done with wait')
    asyncio.gather(
        loop.create_task(
            connector_connection.send_notification({'notify_msg': 'consider yourself notified -- set 1'})
        )
    )


if __name__ == '__main__':
    main()
"""},
    {DOC_NAME: 'pynotify2.py', DOC_CONTENTS:
"""
import os
import asyncio

some_list = []
three = 3
somevar = {'message': 'from Python'}
where_are_we = os.getcwd()

async def looper(count):
    global some_list
    global connector_connection
    for k in range(count):
         some_list.append('I am list item {0}'.format(k))
    await connector_connection.send_notification({'notify_msg': 'consider yourself notified -- set 2'})

def main():
    print('Looper -- in main')
    loop = asyncio.get_event_loop()
    tasks = []
    tasks.append(loop.create_task(looper(25)))
    print('Looper -- about to wait for the loop')
    asyncio.gather(*tasks)
    print('Looper -- done with wait')


if __name__ == '__main__':
    main()
"""},
    {DOC_NAME: 'pynotify3.py', DOC_CONTENTS:
    """
import os
import asyncio

some_list = []
three = 3
somevar = {'message': 'from Python'}
where_are_we = os.getcwd()

async def looper(count):
    global some_list
    global connector_connection
    await connector_connection.send_notification({'notify_msg': 'consider yourself notified -- set 3'})
    for m in range(count):
         some_list.append('I am list item {0}'.format(m))

def main():

    print('Looper -- in main')
    loop = asyncio.get_event_loop()
    tasks = []
    tasks.append(loop.create_task(looper(25)))
    print('Looper -- about to wait for the loop')
    asyncio.gather(*tasks)
    print('Looper -- done with wait')


if __name__ == '__main__':
    main()
"""}
]

py_code_self_returning = [
"""
import os
import asyncio

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
            print('Looper: In loop, iteration: ', i)
            await connector_connection.send_query_response(connector_context, 100, 
                                                           {'item': 'I am list item {0}'.format(i)})
        await connector_connection.send_query_response(connector_context, 200, {'item': 'I am the last item'})
        await connector_connection.send_notification({'notify_msg': 'note this code set 1'})

        print('Connector connection is: ', connector_connection)
        print('Looper: Loop completed')
    except Exception as exc:
        print('Looper TRAPPED EXCEPTION', exc)

def main():
    print('Looper -- in main')
    loop = asyncio.get_event_loop()
    tasks = []
    tasks.append(loop.create_task(looper(25)))
    print('Looper -- about to wait for the loop')
    asyncio.gather(*tasks)
    print('Looper -- done with wait')

    
if __name__ == '__main__':
    main()
""",
"""
import os
import asyncio

three = 3
somevar = {'message': 'from Python'}
where_are_we = os.getcwd()

async def looper(count):
    global connector_context
    global connector_connection
    print('Looper: Context: ', connector_context)
    print('Looper: Our source is: ', connector_connection.get_source())

    try:
        i = 0
        j = 1
        while i < (count-1):
            payload = []
            for l in range(min(j, count - 1 - i)):
                payload.append({'item': 'I am list item {0}'.format(i)})
            print('Looper: In loop, iteration: ', i, ' size:', len(payload), 'payload:', payload)
            i += len(payload)
            j = (j + 1) if j < 5 else 1
            if len(payload) == 1:
                await connector_connection.send_query_response(connector_context, 100, payload[0])
            else:
                await connector_connection.send_query_response(connector_context, 100, payload)
        await connector_connection.send_notification({'notify_msg': 'note this code set 2'})
        await connector_connection.send_query_response(connector_context, 200, {'item': 'I am the last item'})

        print('Connector connection is: ', connector_connection)
        print('Looper: Loop completed')
    except Exception as exc:
        print('Looper TRAPPED EXCEPTION', exc)

def main():
    print('Looper -- in main for batch responses')
    loop = asyncio.get_event_loop()
    tasks = []
    tasks.append(loop.create_task(looper(25)))
    print('Looper -- about to wait for the loop')
    asyncio.gather(*tasks)
    print('Looper -- done with wait')


if __name__ == '__main__':
    main()
""",
"""
import os
import asyncio

three = 3
somevar = {'message': 'from Python'}
where_are_we = os.getcwd()

async def looper(count):
    global connector_context
    global connector_connection
    print('Looper: Context: ', connector_context)
    print('Looper: Our source is: ', connector_connection.get_source())

    try:
        i = 0
        j = 1
        while i < (count):
            payload = []
            for l in range(min(j, count - i)):
                payload.append({'item': 'I am list item {0}'.format(i)})
            print('Looper: In loop, iteration: ', i, ' size:', len(payload), 'payload:', payload)
            i += len(payload)
            j = (j + 1) if j < 5 else 1
            if len(payload) == 1:
                await connector_connection.send_query_response(connector_context, 100, payload[0])
            else:
                await connector_connection.send_query_response(connector_context, 100, payload)
        await connector_connection.send_query_response(connector_context, 204, None)
        await connector_connection.send_notification({'notify_msg': 'note this code set 3'})

        print('Connector connection is: ', connector_connection)
        print('Looper: Loop completed')
    except Exception as exc:
        print('Looper TRAPPED EXCEPTION', exc)

def main():
    print('Looper -- in main for batch responses')
    loop = asyncio.get_event_loop()
    tasks = []
    tasks.append(loop.create_task(looper(25)))
    print('Looper -- about to wait for the loop')
    asyncio.gather(*tasks)
    print('Looper -- done with wait')


if __name__ == '__main__':
    main()
"""
]

py_code_documents = [
    {DOC_NAME: 'python1.py', DOC_CONTENTS:
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
    looper(25)
"""},
    {DOC_NAME: 'python2.py', DOC_CONTENTS:
"""
import os

some_list = []
three = 3
somevar = {'message': 'from Python'}
where_are_we = os.getcwd()

def looper(count):
    global some_list
    for j in range(count):
         some_list.append('I am list item {0}'.format(j))

if __name__ == '__main__':
    looper(25)
    """},
    {DOC_NAME: 'python3.py', DOC_CONTENTS:
"""
import os

some_list = []
three = 3
somevar = {'message': 'from Python'}
where_are_we = os.getcwd()

def looper(count):
    global some_list
    for k in range(count):
         some_list.append('I am list item {0}'.format(k))

if __name__ == '__main__':
    looper(25)
"""},
    {DOC_NAME: 'python4.py', DOC_CONTENTS:
"""
import os

some_list = []
three = 3
somevar = {'message': 'from Python'}
where_are_we = os.getcwd()

def looper(count):
    global some_list
    for l in range(count):
         some_list.append('I am list item {0}'.format(l))

if __name__ == '__main__':
    looper(25)
    """},
    {DOC_NAME: 'python5.py', DOC_CONTENTS:
"""
import os

some_list = []
three = 3
somevar = {'message': 'from Python'}
where_are_we = os.getcwd()

def looper(count):
    global some_list
    for m in range(count):
         some_list.append('I am list item {0}'.format(m))

if __name__ == '__main__':
    looper(25)
"""}
]

py_code_simple = [
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
    looper(25)
""",
"""
import os

some_list = []
three = 3
somevar = {'message': 'from Python'}
where_are_we = os.getcwd()

def looper(count):
    global some_list
    for j in range(count):
         some_list.append('I am list item {0}'.format(j))

if __name__ == '__main__':
    looper(25)
    """,
     """
import os

some_list = []
three = 3
somevar = {'message': 'from Python'}
where_are_we = os.getcwd()

def looper(count):
    global some_list
    for k in range(count):
         some_list.append('I am list item {0}'.format(k))

if __name__ == '__main__':
    looper(25)
""",
"""
import os

some_list = []
three = 3
somevar = {'message': 'from Python'}
where_are_we = os.getcwd()

def looper(count):
    global some_list
    for l in range(count):
         some_list.append('I am list item {0}'.format(l))

if __name__ == '__main__':
    looper(25)
    """,
     """
import os

some_list = []
three = 3
somevar = {'message': 'from Python'}
where_are_we = os.getcwd()

def looper(count):
    global some_list
    for m in range(count):
         some_list.append('I am list item {0}'.format(m))

if __name__ == '__main__':
    looper(25)
"""
]


if not os.path.exists('serverConfig'):
    os.makedirs('serverConfig')
lg = open('serverConfig/logger.ini', mode='wt')
lg.write(loginit)
lg.close()


async def run_server_test(port, config, qry_count: int, note_count: int, conn_info: bool, cache_size: int,
                          connector: Connectors, server_conc_count, do_self_return: bool, use_documents: bool):
    testserver.reset_results()
    tasks = []
    loop = asyncio.get_event_loop()
    # (port, config, qry_count, note_count, conn_info: bool, cache_size_spec=0, server_conc_count=0):
    tasks.append(loop.create_task(run_server(port, config, qry_count, note_count, conn_info,
                                             cache_size, server_conc_count, do_self_return, use_documents)))
    tasks.append(loop.create_task(run_client_test(connector)))
    await asyncio.gather(*tasks)
    for t in tasks:
        if not t.done():
            t.cancel()


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

    def setup_method(self):
        """This resets things before each test"""
        testserver.reset_results()
        testserver.set_py_code(py_code_simple)
        testserver.set_py_documents(py_code_documents)

    async def do_connector_test_raw_code(self, port, use_conn_info: bool, qry_count: int, at_once: int,
                                         notify_count: int, cache_size: int, do_self_return=False):
        await self.do_connector_test(port, use_conn_info, qry_count, at_once,
                                     notify_count, cache_size, do_self_return, False)

    async def do_connector_test_documents(self, port, use_conn_info: bool, qry_count: int, at_once: int,
                                          notify_count: int, cache_size: int, do_self_return=False):
        await self.do_connector_test(port, use_conn_info, qry_count, at_once,
                                     notify_count, cache_size, do_self_return, True)

    async def do_connector_test(self, port, use_conn_info: bool, qry_count: int, at_once: int,
                                notify_count: int, cache_size: int, do_self_return: bool, use_documents: bool):
        # Construct a server config file to use...
        assert at_once == 0 or qry_count % at_once == 0  # Test config failure -- at once must be multiples
        source_name = 'pythonTestSource'
        cf = f'''
        targetServer=http://localhost:{port}
        authToken=testtoken
        sources={source_name}
                    '''
        connectors = None
        try:
            scf = open('serverConfig/server.config', encoding='utf-8', mode='wt')
            scf.write(cf)
            filename = scf.name
            scf.close()
            connectors = Connectors()
            expected_message_count = qry_count

            await run_server_test(port, filename, expected_message_count, notify_count,
                                  use_conn_info, cache_size, connectors, at_once, do_self_return, use_documents)
            server_res = get_results()
            if use_conn_info:
                if use_documents:
                    code_to_check = testserver.get_py_documents()
                else:
                    code_to_check = testserver.get_py_code()
                assert server_res['exec_calls'] == expected_message_count
                assert server_res['compile_calls'] == len(code_to_check)
                assert server_res['cache_additions'] == len(code_to_check)
                assert server_res['returned_results'] == expected_message_count * 4
                assert server_res['sans_connector_info'] == 0
            else:
                print('Server results: ', server_res, ', do_self_return: ', do_self_return)
                assert server_res['exec_calls'] == 0
                assert server_res['compile_calls'] == 0
                assert server_res['cache_additions'] == 0
                if not do_self_return:
                    assert server_res['returned_results'] == expected_message_count * 4
                else:
                    assert server_res['returned_results'] == expected_message_count * 25  # FIXME
                assert server_res['sans_connector_info'] == expected_message_count
        finally:
            os.remove('serverConfig/server.config')
            if connectors is not None:
                await connectors.connector_set.close()

    def mock_documents(self, mocked, doc_source):
        """This section sets up all the mocked responses for the Vantiq SDK to use."""

        for doc in doc_source:
            mod_time_for_docs = datetime.now().strftime('%Y-%m-%dT%H:%M:%S.%fZ')
            doc_resp = {'name': doc[DOC_NAME],
                        'contentSize': len(doc[DOC_CONTENTS]),
                        'isIncomplete': False,
                        'fileType': 'text/x-python',
                        'content': '/docs/' + doc[DOC_NAME],
                        'ars_modifiedAt': mod_time_for_docs}
            print('Mocking up:', f'/api/v1/resources/documents/{doc[DOC_NAME]}')
            # Since we loop these around more than once, make sure we tell aioresponses not to delete after 1 use
            mocked.get(f'/api/v1/resources/documents/{doc[DOC_NAME]}', status=200, repeat=True, payload=doc_resp)
            mocked.get(doc_resp['content'], status=200, body=doc[DOC_CONTENTS], repeat=True)

    @pytest.mark.asyncio
    @pytest.mark.timeout(10)
    async def test_single_queries(self, unused_tcp_port):
        await self.do_connector_test_raw_code(unused_tcp_port, True, 50, 0, 0, 10)

    @pytest.mark.asyncio
    @pytest.mark.timeout(10)
    async def test_single_doc_queries(self, unused_tcp_port):
        # To run this test, se have to mock up the query returns representing the VantiqSDK
        with aioresponses() as mocked:
            mocked.get('/authenticate',
                       status=200,
                       headers={'contentType': 'application/json'},
                       body=json.dumps({'accessToken': '1234abcd', 'idToken': 'longer_token'}))

            self.mock_documents(mocked, py_code_documents)

            await self.do_connector_test_documents(unused_tcp_port, True, 50, 0, 0, 10)

    @pytest.mark.asyncio
    @pytest.mark.timeout(10)
    async def test_single_doc_queries_multiple(self, unused_tcp_port):
        # To run this test, se have to mock up the query returns representing the VantiqSDK
        with aioresponses() as mocked:
            mocked.get('/authenticate',
                       status=200,
                       headers={'contentType': 'application/json'},
                       body=json.dumps({'accessToken': '1234abcd', 'idToken': 'longer_token'}))
            self.mock_documents(mocked, py_code_documents)
            await self.do_connector_test_documents(unused_tcp_port, True, 50, 10, 0, 10)

    @pytest.mark.asyncio
    @pytest.mark.timeout(10)
    async def test_single_doc_queries_multiple_notify(self, unused_tcp_port):
        testserver.set_py_documents(py_docs_with_notifies)

        # To run this test, se have to mock up the query returns representing the VantiqSDK
        with aioresponses() as mocked:
            mocked.get('/authenticate',
                       status=200,
                       headers={'contentType': 'application/json'},
                       body=json.dumps({'accessToken': '1234abcd', 'idToken': 'longer_token'}))
            self.mock_documents(mocked, py_docs_with_notifies)
            await self.do_connector_test_documents(unused_tcp_port, True, 50, 0, 50, 10)

    @pytest.mark.asyncio
    @pytest.mark.timeout(10)
    async def test_single_queries_noci(self, unused_tcp_port):
        await self.do_connector_test_raw_code(unused_tcp_port, False, 50, 0, 0, 10)

    @pytest.mark.asyncio
    @pytest.mark.timeout(10)
    async def test_multiple_queries_ci(self, unused_tcp_port):
        await self.do_connector_test_raw_code(unused_tcp_port, True, 50, 10, 0, 10)

    @pytest.mark.asyncio
    @pytest.mark.timeout(10)
    async def test_multiple_queries_noci(self, unused_tcp_port):
        await self.do_connector_test_raw_code(unused_tcp_port, False, 50, 10, 0, 10)

    @pytest.mark.asyncio
    @pytest.mark.timeout(10)
    async def test_single_queries_with_notify(self, unused_tcp_port):
        testserver.set_py_code(py_code_with_notifies)
        await self.do_connector_test_raw_code(unused_tcp_port, True, 50, 0, 50, 10)

    @pytest.mark.asyncio
    @pytest.mark.timeout(10)
    async def test_pycode_does_return(self, unused_tcp_port):
        testserver.set_py_code(py_code_self_returning)
        # Have to turn off connection info since callees don't send it
        await self.do_connector_test_raw_code(unused_tcp_port, False, 75, 0, 75, 10, True)

    @pytest.mark.asyncio
    @pytest.mark.timeout(10)
    async def test_pycode_does_return_concurrent(self, unused_tcp_port):
        testserver.set_py_code(py_code_self_returning)
        # Have to turn off connection info since callees don't send it
        await self.do_connector_test_raw_code(unused_tcp_port, False, 75, 15, 75, 10, True)

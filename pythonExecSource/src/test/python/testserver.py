#!/usr/bin/env python
__author__ = 'fhcarter'
__copyright__ = "Copyright 2022, Vantiq, Inc."
__license__ = "MIT License"
__email__ = "support@vantiq.com"

__all__ = [
    'run_server',
    'get_results',
    'set_py_code',
    'get_py_code',
    'set_py_documents',
    'get_py_documents',
    'DOC_NAME',
    'DOC_CONTENTS'

]

import asyncio
import jprops
import json
import logging
from typing import Union
from uuid import uuid4

import websockets
from websockets import WebSocketServer

import vantiqconnectorsdk
from vantiqconnectorsdk import VantiqConnector
import pyExecConnector

props = None
notify_count = 0
send_count = 0
query_count = 0
cache_size = 0
concurrent_count = 0
connector_info = False
return_from_python_code = False
use_documents = False

stop: Union[asyncio.Future,  None] = None
reconnect: Union[asyncio.Future, None] = None
running: Union[asyncio.Future, None] = None
starting: Union[asyncio.Future, None] = None

wait_for_notifications: Union[asyncio.Future, None] = None
wait_for_queries: Union[asyncio.Future, None] = None

cache_additions = 0
returned_results = 0
compile_calls = 0
exec_calls = 0
sans_connector_info = 0

logger = logging.getLogger('vantiq.pyexecconnector.testserver')

DOC_NAME = 'doc_name'
DOC_CONTENTS = 'doc_contents'

py_code_example = []
py_code_documents = []


def set_py_code(py_code):
    global py_code_example
    py_code_example = py_code


def get_py_code():
    global py_code_example
    return py_code_example


def set_py_documents(py_docs):
    global py_code_documents
    py_code_documents = py_docs


def get_py_documents():
    global py_code_documents
    return py_code_documents


def set_conditions(qry_count: int, at_once: int, cache_spec: int, note_count: int,
                   conn_info: bool, do_self_return: bool, use_docs: bool):
    global query_count
    global send_count
    global cache_size
    global notify_count
    global connector_info
    global concurrent_count
    global return_from_python_code
    global use_documents
    query_count = qry_count
    send_count = qry_count
    cache_size = cache_spec
    notify_count = note_count
    connector_info = conn_info
    concurrent_count = at_once
    return_from_python_code = do_self_return
    use_documents = use_docs


def get_results():
    global cache_additions
    global returned_results
    global compile_calls
    global exec_calls
    global sans_connector_info
    res = {'cache_additions': cache_additions,
           'returned_results': returned_results,
           'compile_calls': compile_calls,
           'exec_calls': exec_calls,
           'sans_connector_info': sans_connector_info,
           }
    return res


def reset_results():
    global cache_additions
    global returned_results
    global compile_calls
    global exec_calls
    global sans_connector_info
    global logger
    logger.setLevel(logging.DEBUG)
    cache_additions = 0
    returned_results = 0
    compile_calls = 0
    exec_calls = 0
    sans_connector_info = 0


def message_dumper(message):
    logger.debug('Test server got message: ', message)

    op = '(No-operation)'
    if 'op' in message:
        op = message['op']
        logger.debug('Test server received operation: %s', op)
    # noinspection PyProtectedMember
    if vantiqconnectorsdk._RESOURCE_NAME in message:
        # noinspection PyProtectedMember
        logger.debug('Test server op: %s, resourceName: %s', op, message[vantiqconnectorsdk._RESOURCE_NAME])
    # noinspection PyProtectedMember
    if vantiqconnectorsdk._RESOURCE_ID in message:
        # noinspection PyProtectedMember
        logger.debug('Test server op: %s, resourceId: %s', op, message[vantiqconnectorsdk._RESOURCE_ID])
    if 'object' in message:
        logger.debug('Test server op: %s, object: %s', op, message['object'])


async def handler(websocket):
    global props
    global notify_count
    global send_count
    global concurrent_count
    global connector_info
    global cache_size
    global stop
    global running
    global wait_for_notifications
    global wait_for_queries
    global return_from_python_code
    wait_for_notifications = asyncio.get_event_loop().create_future()
    wait_for_queries = asyncio.get_event_loop().create_future()

    logger.debug('Config properties: %s', props)
    while True:
        try:
            raw_message = await websocket.recv()
            message = json.loads(raw_message)
            message_dumper(message)
            if message['op'] == 'validate':
                ok_message = {'status': 200}
                # noinspection PyUnresolvedReferences
                if 'object' not in message or message['object'] != props['authToken']:
                    ok_message = {'status': 401, 'body': [{'code': 'authFailure', 'message': 'invalid authToken'}]}
                    await websocket.send(json.dumps(ok_message))
                    await asyncio.sleep(10)
                    stop.set_result('Got auth issues')
                else:
                    await websocket.send(json.dumps(ok_message))
                    # Now, we expect to get a connectExtension message
                    raw_message = await websocket.recv()
                    message = json.loads(raw_message)
                    message_dumper(message)

                    # noinspection PyProtectedMember
                    assert vantiqconnectorsdk._RESOURCE_NAME in message
                    assert 'op' in message
                    # noinspection PyProtectedMember
                    assert vantiqconnectorsdk._RESOURCE_ID in message
                    # noinspection PyProtectedMember
                    assert message[vantiqconnectorsdk._OPERATION] == vantiqconnectorsdk._OP_CONNECT_EXTENSION
                    # noinspection PyUnresolvedReferences
                    # noinspection PyProtectedMember
                    assert message[vantiqconnectorsdk._RESOURCE_ID] == props[VantiqConnector.SOURCES]
                    # noinspection PyProtectedMember
                    assert message[vantiqconnectorsdk._RESOURCE_NAME] == VantiqConnector.SOURCES

                    # Now just send some message back
                    # noinspection PyUnresolvedReferences
                    # noinspection PyProtectedMember
                    await websocket.send(json.dumps({"op": "configureExtension",
                                                     "resourceName": vantiqconnectorsdk._SOURCES_RESOURCE,
                                                     "resourceId": props[VantiqConnector.SOURCES],
                                                     'object': {'config':
                                                                {pyExecConnector.PYTHON_EXEC_SECTION:
                                                                 {pyExecConnector.GENERAL_SECTION:
                                                                     {pyExecConnector.RETURN_RUNTIME_INFO:
                                                                      connector_info,
                                                                      pyExecConnector.CODE_CACHE_SIZE: cache_size
                                                                      }
                                                                  }
                                                                 }
                                                                }
                                                     }))

                    running.set_result('Ready to proceed')
                    if notify_count == 0:
                        wait_for_notifications.set_result('Not Used')
                    await asyncio.gather(
                        do_query_operations(websocket, send_count, concurrent_count, return_from_python_code)
                    )
                    await wait_for_notifications
                    await wait_for_queries
                    logger.debug('wait_for_notifications returned: %s', wait_for_notifications.result())
                    logger.debug('Sending _TEST_CLOSE operation to shut things down')
                    notify_count = 0
                    # noinspection PyProtectedMember
                    await websocket.send(json.dumps({'op': vantiqconnectorsdk._TEST_CLOSE}))
                    await asyncio.sleep(0.1)  # Let message get sent before closing server
                    stop.set_result('done')
        except websockets.ConnectionClosed:
            # This is OK -- we sent our client a "go away" message so...
            pass
            break
    assert query_count == 0


def generate_python_code(script_number: int):
    return py_code_example[script_number]


def generate_python_script_ref(script_number: int):
    return py_code_documents[script_number][DOC_NAME]


async def do_query_operations(websocket, snd_count: int, conc_count: int, do_partials=False):
    global query_count
    global wait_for_queries
    global cache_additions
    global compile_calls
    global exec_calls
    global returned_results
    global sans_connector_info
    global notify_count
    global use_documents

    logger.debug('do_query_operations() starting with send_count: %s, using documents: %s', snd_count, use_documents)
    outstanding_queries = {}
    query_count = snd_count
    if conc_count <= 0:
        conc_count = 1
    qry_number = 0
    loop = asyncio.get_event_loop()
    while qry_number < snd_count:
        logger.debug('Sending some queries, notify_count: %s, qry_number: %s, query_count: %s',
                     notify_count, qry_number, query_count)
        qry_tasks = []
        for op_count in range(conc_count):
            query_count -= 1
            this_query = str(uuid4())
            outstanding_queries[this_query] = True
            # noinspection PyUnresolvedReferences
            # noinspection PyProtectedMember
            query_msg = {
                vantiqconnectorsdk._MESSAGE_HEADERS: {vantiqconnectorsdk._ORIGIN_ADDRESS: this_query},
                "op": vantiqconnectorsdk._OP_QUERY,
                "resourceName": vantiqconnectorsdk._SOURCES_RESOURCE,
                "resourceId": props[VantiqConnector.SOURCES]
            }
            if use_documents:
                script_to_use = generate_python_script_ref((qry_number + op_count) % len(py_code_documents))

                query_msg['object'] = {
                    'script': script_to_use,
                    pyExecConnector.EXEC_HANDLES_RETURN: do_partials,
                    'name': 'pythonScript{0}'.format((qry_number + op_count) % len(py_code_example))
                }
            else:
                code_to_use = generate_python_code((qry_number + op_count) % len(py_code_example))
                query_msg['object'] = {
                    'code': code_to_use,
                    pyExecConnector.EXEC_HANDLES_RETURN: do_partials,
                    'name': 'pythonScript{0}'.format((qry_number + op_count) % len(py_code_example))
                }
            qry_tasks.append(loop.create_task(websocket.send(json.dumps(query_msg))))
        assert len(qry_tasks) == conc_count
        await asyncio.gather(*qry_tasks)

        logger.debug('Sent the query, awaiting responses...')
        # Now, we've sent all of our queries.  Wait for the responses & match them up...

        resp_count = 0
        while resp_count < conc_count:
            raw_msg: Union[str, None] = await websocket.recv()
            resp = json.loads(raw_msg)
            logger.debug('Server received message: %s', resp)

            # noinspection PyProtectedMember
            if vantiqconnectorsdk._STATUS in resp:
                # noinspection PyProtectedMember
                assert vantiqconnectorsdk._STATUS in resp
                # noinspection PyProtectedMember
                status = resp[vantiqconnectorsdk._STATUS]
                assert 'headers' in resp
                # noinspection PyProtectedMember
                assert vantiqconnectorsdk._RESPONSE_ADDRESS_HEADER in resp['headers']
                # noinspection PyProtectedMember
                response_to = resp['headers'][vantiqconnectorsdk._RESPONSE_ADDRESS_HEADER]
                assert response_to in outstanding_queries
                assert outstanding_queries[response_to]

                # noinspection PyProtectedMember
                if status in [VantiqConnector.QUERY_EMPTY, VantiqConnector.QUERY_COMPLETE]:
                    body = None
                    if status == VantiqConnector.QUERY_COMPLETE:
                        # noinspection PyProtectedMember
                        assert vantiqconnectorsdk._BODY in resp
                        # noinspection PyProtectedMember
                        body = resp[vantiqconnectorsdk._BODY]
                    else:
                        # noinspection PyProtectedMember
                        assert vantiqconnectorsdk._BODY not in resp
                    if not do_partials:
                        assert pyExecConnector.SCRIPT_RESULTS in body
                    outstanding_queries.pop(response_to)
                    if body is not None:
                        assert (pyExecConnector.CONNECTOR_INFO in body) == connector_info
                    if connector_info:
                        conn_info = body[pyExecConnector.CONNECTOR_INFO]
                        logger.debug('Connector Info: %s', conn_info)
                        if pyExecConnector.EXECUTION_TIME in conn_info:
                            exec_calls += 1
                        if pyExecConnector.COMPILE_TIME in conn_info:
                            compile_calls += 1
                        if pyExecConnector.NEW_CACHE_ENTRY in conn_info and conn_info[pyExecConnector.NEW_CACHE_ENTRY]:
                            cache_additions += 1
                    else:
                        sans_connector_info += 1
                    if not do_partials:
                        returned_results += len(body[pyExecConnector.SCRIPT_RESULTS])
                    elif body is not None:
                        if isinstance(body, list):
                            returned_results += len(body)
                        else:
                            returned_results += 1
                    resp_count += 1
                elif resp[vantiqconnectorsdk._STATUS] == VantiqConnector.QUERY_PARTIAL:
                    # Then we're testing the partial results.
                    logger.debug('Found a partial result: %s', resp)
                    # noinspection PyProtectedMember
                    assert vantiqconnectorsdk._BODY in resp
                    # noinspection PyProtectedMember
                    body = resp[vantiqconnectorsdk._BODY]
                    if isinstance(body, list):
                        returned_results += len(body)
                    else:
                        returned_results += 1
                else:
                    logger.error('Found error in response stream: %s', resp)
                    # noinspection PyProtectedMember
                    assert resp[vantiqconnectorsdk._STATUS] in [100, 200, 204]
            elif vantiqconnectorsdk._OPERATION in resp and \
                    resp[vantiqconnectorsdk._OPERATION] == vantiqconnectorsdk._OP_NOTIFY:
                acknowledge_notify(raw_msg)
            else:
                # Oops -- this shouldn't happen
                assert resp is None
        # Between our query sets, we should have no outstanding queries
        assert len(outstanding_queries) == 0
        qry_number += conc_count

    logger.debug('do_query_op() has completed with a remaining query_count: %s', query_count)
    assert query_count >= 0  # Failure here is a test setup/operation error
    if query_count <= 0:
        wait_for_queries.set_result(0)
    while notify_count > 0:
        logger.debug('do_query_op: cleaning up outstanding notifications, notify_count: %s', notify_count)
        raw_msg: Union[str, None] = await websocket.recv()
        resp = json.loads(raw_msg)
        # noinspection PyProtectedMember
        assert vantiqconnectorsdk._OPERATION in resp and \
               resp[vantiqconnectorsdk._OPERATION] == vantiqconnectorsdk._OP_NOTIFY
        acknowledge_notify(raw_msg)
        if wait_for_notifications.done():
            break


async def force_reconnect_dance():
    global reconnect
    global running
    global starting
    logger.debug('force_reconnect_dance(): disconnect requested')
    running.cancel()
    starting = asyncio.get_event_loop().create_future()
    reconnect.set_result('Please disco/reco')
    await starting
    await running


def acknowledge_notify(msg):
    global notify_count
    notify_count -= 1
    message = json.loads(msg)
    logger.debug('Server: (notifies remaining: %s) Got message: %s', notify_count, message)
    if wait_for_notifications is not None and notify_count == 0:
        wait_for_notifications.set_result(notify_count)


async def run_server(port, config, qry_count, note_count, conn_info: bool, cache_size_spec=0, server_conc_count=0,
                     do_self_return=False, use_docs: bool = False):
    global stop
    global reconnect
    global running
    global starting

    set_conditions(qry_count, server_conc_count, cache_size_spec, note_count, conn_info, do_self_return, use_docs)

    cf = None
    try:
        cf = open(config)
        global props
        props = jprops.load_properties(cf)
        loop = asyncio.get_event_loop()

        # The stop condition is set when receiving SIGTERM.
        stop = loop.create_future()
        starting = loop.create_future()
        while not stop.done():
            reconnect = loop.create_future()
            # stop is only set when we are truly done so there's no need to fix it here.
            stop_conditions = [stop, reconnect]
            if starting is None or starting.done():
                starting = asyncio.get_event_loop().create_future()

            if running is None or running.done():
                running = asyncio.get_event_loop().create_future()
            server: WebSocketServer = await websockets.serve(handler, "", port)
            ack = 'Running server on port: {0}'.format(port)
            starting.set_result(ack)
            logger.debug(ack)
            await asyncio.wait(stop_conditions, return_when=asyncio.FIRST_COMPLETED)
            logger.debug('stop conditions returned: stop, reconnect: %s, %s', stop.done(), reconnect.done())
            server.close()

            # Need to cancel our outstanding waiters so that our close can complete.
            # Otherwise, we hang in the `await server.wait_closed()` call below
            if starting is not None and not starting.done():
                starting.cancel()
            if running is not None and not running.done():
                running.cancel()
            if reconnect is not None and not reconnect.done():
                reconnect.cancel()
            logger.debug('Server on port %s is attempting close operation', port)
            await server.wait_closed()
            logger.debug('Server on port %s has completed close operation', port)

    finally:
        if stop is not None and not stop.done():
            stop.cancel('Terminating')
        cf.close()

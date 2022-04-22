#!/usr/bin/env python

import asyncio

import jprops
import websockets
from websockets import WebSocketServer
import json
import vantiqconnectorsdk
from vantiqconnectorsdk import VantiqConnector
from typing import Union

props = None
publish_count = 0
notify_count = 0
disconnect_count = 0

stop: Union[asyncio.Future, None] = None
reconnect: Union[asyncio.Future, None] = None
running: Union[asyncio.Future, None] = None
starting: Union[asyncio.Future, None] = None

wait_for_notifications: Union[asyncio.Future, None] = None
wait_for_publications: Union[asyncio.Future, None] = None


def message_dumper(message):
    print('Test server got message: ', message)

    if 'op' in message:
        op = message['op']
        print('Test server received operation:', op)
    if vantiqconnectorsdk._RESOURCE_NAME in message:
        print('Test server', op, 'resourceName: ', message[vantiqconnectorsdk._RESOURCE_NAME])
    if vantiqconnectorsdk._RESOURCE_ID in message:
        print('Test server', op, 'resourceId:', message[vantiqconnectorsdk._RESOURCE_ID])
    if 'object' in message:
        print('Test server', op, 'object:', message['object'])


async def handler(websocket):
    global props
    global notify_count
    global publish_count
    global disconnect_count
    global stop
    global running
    global wait_for_notifications
    global wait_for_publications
    wait_for_notifications = asyncio.get_event_loop().create_future()
    wait_for_publications = asyncio.get_event_loop().create_future()

    print('Config properties: ', props)
    while True:
        try:
            raw_message = await websocket.recv()
            message = json.loads(raw_message)
            message_dumper(message)
            if message['op'] == 'validate':
                ok_message = {'status': 200}
                if 'object' not in message or message['object'] != props['authToken']:
                    ok_message = {'status': 401, 'body': [{'code': 'authFailure', 'message': 'invalid authToken'}]}
                    await websocket.send(json.dumps(ok_message))
                    if not stop.done():
                        stop.set_result('Got auth issues')
                    # Once our bad auth test is done, our simple server simulator should exit.  The stop condition
                    # is set above, but we also need to break out of our reconnect loop.
                    break
                else:
                    await websocket.send(json.dumps(ok_message))
                    # Now, we expect to get a connectExtension message
                    raw_message = await websocket.recv()
                    message = json.loads(raw_message)
                    message_dumper(message)

                    assert vantiqconnectorsdk._RESOURCE_NAME in message
                    assert 'op' in message
                    assert vantiqconnectorsdk._RESOURCE_ID in message
                    assert message[vantiqconnectorsdk._OPERATION] == vantiqconnectorsdk._OP_CONNECT_EXTENSION
                    assert message[vantiqconnectorsdk._RESOURCE_ID] == props[VantiqConnector.SOURCES]
                    assert message[vantiqconnectorsdk._RESOURCE_NAME] == VantiqConnector.SOURCES

                    # Now just send some message back
                    await websocket.send(json.dumps({"op": "configureExtension", "object":
                                                     {'config': {'someProp': "message content"}}}))

                    running.set_result('Ready to proceed')
                    await asyncio.gather(
                        wait_for_receives(websocket, notify_count),
                        do_publishes(websocket, publish_count, disconnect_count)
                    )
                    notify_count = 0
                    await wait_for_notifications
                    await wait_for_publications
                    print('wait_for_notifications returned:', wait_for_notifications.result())
                    print('Sending _TEST_CLOSE operation to shut things down')
                    await websocket.send(json.dumps({'op': vantiqconnectorsdk._TEST_CLOSE}))
                    await asyncio.sleep(0.1)  # Let message get sent before closing server
                    stop.set_result('done')
        except websockets.ConnectionClosed:
            # This is OK -- we sent our client a "go away" message so...
            pass
            break


async def do_publishes(websocket, pub_count: int, disc_count: int):
    global publish_count
    global wait_for_publications
    print('do_publishes() starting with publish_count:', publish_count)
    for i in range(pub_count):
        if disc_count > 0 and i > 0 and (i % disc_count == 0):
            await force_reconnect_dance()
        publish_count -= 1
        await websocket.send(json.dumps({"op": vantiqconnectorsdk._OP_PUBLISH,
                                         'object': {"stuff": "junk", "count": i}}))

        # Inject these very brief "yields" to get a better intermixed behavior for the tests
        # Due to the coroutine nature of asyncio, without them, we tend to get all the notifies
        # then all the publishes.  That still happens sometimes, but these very slight pauses
        # allow other parts of the test to operate.  This more thoroughly exercises the SDK.
        await asyncio.sleep(0.01)  # Yield CPU so that we get some better intermixing of operations

    print('do_publishes() has completed with a remaining publish_count:', publish_count)
    if publish_count == 0:
        wait_for_publications.set_result(0)


async def force_reconnect_dance():
    global reconnect
    global running
    global starting
    print('force_reconnect_dance(): disconnect requested')
    running.cancel()
    starting = asyncio.get_event_loop().create_future()
    reconnect.set_result('Please disco/reco')
    await starting
    await running


async def wait_for_receives(websocket, note_count: int):
    global notify_count
    global wait_for_notifications
    print('wait_for_receives() starting with notify_count: {0} & note_count: {1}'.format(notify_count, note_count))
    try:
        for i in range(note_count):
            try:
                msg = await websocket.recv()
                notify_count -= 1
                message = json.loads(msg)
                print('Server: Got message: ', message)
            except websockets.ConnectionClosed:
                await asyncio.sleep(2)  # Let server start up
                pass
    except Exception as e:
        print('Unexpected exception during wait_for_receives(): {0}'.format(type(e).__name__))
    print('wait_for_receives() has completed')
    if wait_for_notifications is not None:
        wait_for_notifications.set_result(note_count)


async def do_timeout(delay):
    await asyncio.sleep(delay)
    raise TimeoutError


async def run_server(port, config, pub_count, note_count=0, server_disc_count=0):
    global publish_count
    global notify_count
    global disconnect_count
    global stop
    global reconnect
    global running
    global starting
    publish_count = pub_count
    notify_count = note_count
    disconnect_count = server_disc_count
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
            print(ack)
            await asyncio.wait(stop_conditions, return_when=asyncio.FIRST_COMPLETED)
            print('stop conditions returned: stop, reconnect:', stop.done(), reconnect.done())
            server.close()

            # Need to cancel our outstanding waiters so that our close can complete.
            # Otherwise, we hang in the `await server.wait_closed()` call below
            if starting is not None and not starting.done():
                starting.cancel()
            if running is not None and not running.done():
                running.cancel()
            if reconnect is not None and not reconnect.done():
                reconnect.cancel()
            print('Server on port {0} is attempting close operation'.format(port))
            await server.wait_closed()
            print('Server on port {0} has completed close operation'.format(port))

    finally:
        if stop is not None and not stop.done():
            stop.cancel()
            await stop
        cf.close()

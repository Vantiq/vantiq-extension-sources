package io.vantiq.extsrc.opcua.uaOperations;

import org.eclipse.milo.examples.server.ExampleServer;
import org.junit.After;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.fail;

public class OpcUaTestBase {
    protected  ExampleServer exampleServer = null;
    @Before
    public void setup() {
        try {
            exampleServer = new ExampleServer();
            exampleServer.startup().get();
        }
        catch (Exception e) {
            fail("Trapped exception during ExampleServer startup: " + Utils.errFromExc(e));
        }

    }

    @After
    public void cleanup() {
        try {
            if (exampleServer != null) {
                exampleServer.shutdown().get();
            }
        } catch (Exception e) {
            fail("Trapped exception during shutdown: " + Utils.errFromExc(e));
        }
    }
}

package io.vantiq.extsrc.opcua.uaOperations;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.fail;

@Slf4j
public class ServerRun extends OpcUaTestBase {

    @Test
    public void testRunAWhile() {
        try {
            Thread.sleep(10 * 60 * 1000); // Run for 10 minutes
        }
        catch (InterruptedException e) {
            System.out.println("Interrupted...)");
        }

    }
}

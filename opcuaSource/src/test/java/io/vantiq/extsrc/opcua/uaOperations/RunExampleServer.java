package io.vantiq.extsrc.opcua.uaOperations;

import io.vantiq.extsrc.opcua.categories.OnlyHandTests;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;


/**
 * This class is just present as a simple way run the (milo) example server as an external server.
 * Simply running this "test" will start the server then then stall for the given amount of time.
 *
 */
@Slf4j
@Category(OnlyHandTests.class)
public class RunExampleServer extends OpcUaTestBase {

    public static final Integer ONE_MINUTE = 60 * 1000;
    public static final Integer DURATION_30_MINUTES = 30 * ONE_MINUTE;
    public static final Integer DURATION_10_MINUTES = 10 * ONE_MINUTE;
    @Test
    @Category(OnlyHandTests.class)
    public void testRunAWhile() {
        try {
            Thread.sleep(DURATION_30_MINUTES);
        }
        catch (InterruptedException e) {
            System.out.println("Interrupted...)");
        }

    }
}

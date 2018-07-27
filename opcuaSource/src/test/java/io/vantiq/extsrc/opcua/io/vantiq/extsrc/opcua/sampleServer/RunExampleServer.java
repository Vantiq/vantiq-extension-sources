package io.vantiq.extsrc.opcua.io.vantiq.extsrc.opcua.sampleServer;


import io.vantiq.extsrc.opcua.uaOperations.Utils;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.milo.examples.server.ExampleServer;

import java.util.concurrent.TimeUnit;


/**
 * This class is just present as a simple way run the (milo) example server as an external server.
 * Simply running this "test" will start the server then then stall for the given amount of time.
 *
 */
@Slf4j
public class RunExampleServer {
    public static final long DURATION_30_MINUTES = TimeUnit.MINUTES.toMillis(30);
    public static final long DURATION_10_MINUTES = TimeUnit.MINUTES.toMillis(10);
    public static final long DURATION_1_DAY = TimeUnit.DAYS.toMillis(1);

    public static long durationOfRun = DURATION_30_MINUTES;

    public void testRunAWhile() {
        try {
            long hours = TimeUnit.MILLISECONDS.toHours(durationOfRun);
            long minutes = TimeUnit.MILLISECONDS.toMinutes(durationOfRun)
                    - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(durationOfRun));
            long seconds = TimeUnit.MILLISECONDS.toSeconds(durationOfRun)
                    - TimeUnit.MINUTES.toSeconds(minutes) - TimeUnit.HOURS.toSeconds(hours);
            String durationString = String.format("%d hours %d min, %d sec",
                    hours, minutes, seconds);

            ExampleServer exampleServer = new ExampleServer();
            exampleServer.startup().get();

            log.info("Running example server for {}", durationString);

            Thread.sleep(durationOfRun);
        }
        catch (InterruptedException e) {
            System.out.println("Interrupted...)");
        }
        catch (Exception e)
        {
            log.error("Trapped exception during ExampleServer startup: " + Utils.errFromExc(e));
        }
    }

    public static void main(String[] argv) {
        if (argv.length < 1) {
            durationOfRun = DURATION_1_DAY;
        } else {
            String durationSpec = argv[0];
            int minTime = Integer.valueOf(durationSpec);

            durationOfRun = TimeUnit.MINUTES.toMillis(minTime);
        }
        RunExampleServer server = new RunExampleServer();
        server.testRunAWhile();
    }
}

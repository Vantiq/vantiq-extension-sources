package io.vantiq.extsrc.testConnector;

import io.vantiq.extjsdk.ExtensionServiceMessage;
import io.vantiq.extjsdk.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class TestConnectorHandleConfiguration extends Handler<ExtensionServiceMessage> {
    Logger log;
    String sourceName;
    TestConnectorCore core;

    boolean configComplete = false; // Not currently used

    // Constants for getting config options
    private static final String CONFIG = "config";
    private static final String TEST_CONFIG = "testConfig";
    private static final String GENERAL = "general";
    private static final String POLLING_INTERVAL = "pollingInterval";

    // Default pollingInterval if not specified (1 minute in millis)
    private static final int DEFAULT_POLLING_INTERVAL = 60000;

    public TestConnectorHandleConfiguration(TestConnectorCore core) {
        this.core = core;
        this.sourceName = core.getSourceName();
        log = LoggerFactory.getLogger(this.getClass().getCanonicalName() + "#" + sourceName);
    }

    @Override
    public void handleMessage(ExtensionServiceMessage message) {
        Map<String, Object> configObject = (Map) message.getObject();
        Map<String, Object> config;
        Map<String, Object> testConfig;
        Map<String, Object> general;

        // Obtain entire config from the message object
        if (!(configObject.get(CONFIG) instanceof Map)) {
            log.error("Configuration failed. No configuration suitable for Test Connector.");
            failConfig();
            return;
        }
        config = (Map) configObject.get(CONFIG);

        // Retrieve the jdbcConfig and the vantiq config
        if (!(config.get(TEST_CONFIG) instanceof Map)) {
            log.error("Configuration failed. Configuration must contain 'testConfig' field.");
            failConfig();
            return;
        }
        testConfig = (Map) config.get(TEST_CONFIG);

        // Get the general options from the jdbcConfig
        if (!(testConfig.get(GENERAL) instanceof Map)) {
            log.error("Configuration failed. No general options specified.");
            failConfig();
            return;
        }
        general = (Map) testConfig.get(GENERAL);

        // Call method to setup the connector with the source configuration
        setupTestConnector(general);
    }

    public void setupTestConnector(Map generalConfig) {
        // Grab filenames if they were configured and start polling for data from them
        if (generalConfig.get(TestConnectorCore.FILENAMES) != null &&
                generalConfig.get(TestConnectorCore.FILENAMES) instanceof List) {
            // Make sure that all list elements are strings, otherwise log an error and call failConfig()
            List filenames = (List) generalConfig.get(TestConnectorCore.FILENAMES);
            if (!checkListValues(filenames)) {
                log.error("Configuration failed. Element in 'filename' list was either not a String or empty. All " +
                        "'filenames' list elements must be non-empty Strings.");
                failConfig();
                return;
            }

            int pollingInterval = DEFAULT_POLLING_INTERVAL;
            if (generalConfig.get(POLLING_INTERVAL) instanceof Integer && ((Integer) generalConfig.get(POLLING_INTERVAL)) > 0) {
                pollingInterval = (Integer) generalConfig.get(POLLING_INTERVAL);
            }

            // Now that we have the data, lets start polling
            core.pollFromFiles(filenames, pollingInterval);
        }
    }

    /**
     * Helper method to check that list elements are all non-empty Strings.
     * @param list List of objects to check
     * @return
     */
    public static boolean checkListValues(List list) {
        for (Object listElem : list) {
            if (!(listElem instanceof String) || ((String) listElem).isEmpty()) {
                return false;
            }
        }
        return true;
    }


    /**
     * Closes the source {@link TestConnectorCore} and marks the configuration as completed. The source will
     * be reactivated when the source reconnects, due either to a Reconnect message (likely created by an update to the
     * configuration document) or to the WebSocket connection crashing momentarily.
     */
    private void failConfig() {
        core.close();
        configComplete = true;
    }

    /**
     * Returns whether the configuration handler has completed. Necessary since the sourceConnectionFuture is completed
     * before the configuration can complete, so a program may need to wait before using configured resources.
     * @return  true when the configuration has completed (successfully or not), false otherwise
     */
    public boolean isComplete() {
        return configComplete;
    }
}

package io.vantiq.extsrc.opcua.opcUaSource;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class OpcUaServer {

    public static void main(String[] argv) {
        Map<String,String> connectInfo = new HashMap<>();
        connectInfo.put(OpcUaSource.VANTIQ_URL, "ws://localhost:8080/api/v1/wsock/websocket");
        connectInfo.put(OpcUaSource.VANTIQ_USERNAME, "system");
        connectInfo.put(OpcUaSource.VANTIQ_PASSWORD, "fxtrt$1492");

        OpcUaSource aSource = new OpcUaSource();

        boolean itWorked = aSource.connectToVantiq("sampleUDP", connectInfo);
        log.info("It worked: {}", itWorked);
        System.exit(0);
    }

}

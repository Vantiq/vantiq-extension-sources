package io.vantiq.extsrc.camel;

import static io.vantiq.extjsdk.Utils.AUTH_TOKEN_PROPERTY_NAME;
import static io.vantiq.extjsdk.Utils.SOURCES_PROPERTY_NAME;
import static io.vantiq.extjsdk.Utils.TARGET_SERVER_PROPERTY_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vantiq.extjsdk.Utils;
import org.apache.camel.Endpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

public class VantiqUriTestBase extends CamelTestSupport {
    protected final String exceptionEndpoint = "mock:direct:error";
    
    protected static final String testSourceName = "camelSource";
    protected static final String accessToken = "someAccessToken";
    
    @BeforeAll
    public static void setupSourceConfig() throws Exception {
        File sc = new File(Utils.SERVER_CONFIG_FILENAME);
        sc.deleteOnExit();
        StringBuffer cf = new StringBuffer();
        cf.append(TARGET_SERVER_PROPERTY_NAME).append("=http://localhost:8080").append("\n")
          .append(AUTH_TOKEN_PROPERTY_NAME).append("=").append(accessToken).append("\n")
          .append(SOURCES_PROPERTY_NAME).append("=").append(testSourceName).append("\n");
        Files.write(Path.of(sc.getAbsolutePath()), cf.toString().getBytes());
    }
    
    protected void checkEndpoint(Endpoint ep) throws Exception {
        assert ep instanceof FauxVantiqEndpoint;
        FauxVantiqEndpoint epConsumer = (FauxVantiqEndpoint) ep;
        assert epConsumer.getEndpointUri().contains(accessToken);
        assert epConsumer.getEndpointUri().contains(testSourceName);
        URI senderUri = new URI(epConsumer.getEndpointUri());
        assertEquals("vantiq", senderUri.getScheme(), "Expected Scheme");
        assertEquals("localhost", senderUri.getHost(), "Expected host");
        assertEquals(8080, senderUri.getPort(), "Expeced port");
        String qry = senderUri.getQuery();
        assert qry.contains("accessToken");
        assert qry.contains("sourceName");
        assert qry.contains(accessToken);
        assert qry.contains(testSourceName);
        assert epConsumer.isConnected();
    }
}

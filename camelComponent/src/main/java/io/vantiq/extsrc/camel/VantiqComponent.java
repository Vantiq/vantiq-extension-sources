package io.vantiq.extsrc.camel;

import java.util.Map;

import org.apache.camel.Endpoint;

import org.apache.camel.support.DefaultComponent;

@org.apache.camel.spi.annotations.Component("vantiq")
public class VantiqComponent extends DefaultComponent {
    
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Endpoint endpoint = new VantiqEndpoint(uri, this);
        setProperties(endpoint, parameters);
        return endpoint;
    }
}

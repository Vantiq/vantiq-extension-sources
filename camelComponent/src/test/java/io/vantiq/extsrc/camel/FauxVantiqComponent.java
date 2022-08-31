package io.vantiq.extsrc.camel;

import org.apache.camel.Endpoint;
import org.apache.camel.support.DefaultComponent;

import java.util.Map;

@org.apache.camel.spi.annotations.Component("vantiq")
public class FauxVantiqComponent extends VantiqComponent {
    
    public FauxVantiqEndpoint myEndpoint = null;
    
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Endpoint endpoint = new FauxVantiqEndpoint(uri, this);
        myEndpoint = (FauxVantiqEndpoint) endpoint;
        setProperties(endpoint, parameters);
        return endpoint;
    }
}

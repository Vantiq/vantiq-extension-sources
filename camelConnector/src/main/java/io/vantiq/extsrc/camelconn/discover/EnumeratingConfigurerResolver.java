package io.vantiq.extsrc.camelconn.discover;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.ConfigurerResolver;
import org.apache.camel.spi.PropertyConfigurer;

public class EnumeratingConfigurerResolver implements ConfigurerResolver {
    @Override
    public PropertyConfigurer resolvePropertyConfigurer(String name, CamelContext context) {
        return new NoopConfigurer();
    }
}

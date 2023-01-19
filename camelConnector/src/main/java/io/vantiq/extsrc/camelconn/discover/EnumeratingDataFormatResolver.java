package io.vantiq.extsrc.camelconn.discover;

import static io.vantiq.extsrc.camelconn.discover.EnumeratingComponentResolver.CORE_COMPONENTS_SCHEMES;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.ComponentResolver;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatResolver;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class EnumeratingDataFormatResolver implements DataFormatResolver {
    
    private final Set<String> dataFormatsToLoad;
    private final Set<String> systemDataFormatsUsed;
    
    private final DataFormatResolver originalResolver;
    
    public static final Set<String> CORE_DATAFORMAT_NAMES = new HashSet<>(List.of());
    
    
    public EnumeratingDataFormatResolver(DataFormatResolver origResolver) {
        dataFormatsToLoad = new HashSet<>();
        systemDataFormatsUsed = new HashSet<>();
        originalResolver = origResolver;
    }
    
    @Override
    public DataFormat createDataFormat(String name, CamelContext context) {
        log.debug("Resolver asked to resolve component: {}", name);
        if (CORE_DATAFORMAT_NAMES.contains(name)) {
            log.debug("... but this is a system provided entity, so we'll skip our resolution");
            systemDataFormatsUsed.add(name);
            if (originalResolver != null) {
                return originalResolver.createDataFormat(name, context);
            } else {
                log.error("Original resolved not available to find system component: {}", name);
            }
        } else {
            dataFormatsToLoad.add(name);
        }
        return new PlaceholderDataFormat();
    }
    
    public Set<String> getDataFormatsToLoad() {
        return dataFormatsToLoad;
    }
    
    public Set<String> getSystemDataFormatsUsed() {
        return systemDataFormatsUsed;
    }
}

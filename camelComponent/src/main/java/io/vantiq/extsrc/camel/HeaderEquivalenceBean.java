package io.vantiq.extsrc.camel;

import org.slf4j.Logger;
import java.util.Map;

public class HeaderEquivalenceBean {
    
    private Map<String, String> headerEquivalenceMap;
    
    public Map<String, String> getEquivalenceMap() {
        return headerEquivalenceMap;
    }
    
    public void setEquivalenceMap(Map<String, String> heMap) {
        this.headerEquivalenceMap = heMap;
    }
    
    public void logHeaderEquivalences(Logger log) {
        if (headerEquivalenceMap == null) {
            log.info("No header equivalences found");
        } else {
            log.info("{} header equivalences found", headerEquivalenceMap.size());
            headerEquivalenceMap.forEach( (k, v) -> {
                log.info("\theader: {} mapped to {}", k, v);
            });
        }
    }
}

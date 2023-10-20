/*
 * Copyright (c) 2023 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camel;

import org.slf4j.Logger;
import java.util.Map;

public class HeaderDuplicationBean {
    
    private Map<String, String> headerDuplicationMap;
    
    public Map<String, String> getHeaderDuplicationMap() {
        return headerDuplicationMap;
    }
    
    public void setHeaderDuplicationMap(Map<String, String> heMap) {
        this.headerDuplicationMap = heMap;
    }
    
    public void logHeaderEquivalences(Logger log) {
        if (headerDuplicationMap == null) {
            log.info("No header duplications found");
        } else {
            log.info("{} header duplication found", headerDuplicationMap.size());
            headerDuplicationMap.forEach((k, v) -> {
                log.info("\theader: {} mapped to {}", k, v);
            });
        }
    }
}

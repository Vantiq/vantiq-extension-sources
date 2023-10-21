/*
 * Copyright (c) 2023 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extsrc.camel;

import java.util.Map;

public class HeaderDuplicationBean {
    
    private Map<String, String> headerDuplicationMap;
    
    public Map<String, String> getHeaderDuplicationMap() {
        return headerDuplicationMap;
    }
    
    public void setHeaderDuplicationMap(Map<String, String> heMap) {
        this.headerDuplicationMap = heMap;
    }
}

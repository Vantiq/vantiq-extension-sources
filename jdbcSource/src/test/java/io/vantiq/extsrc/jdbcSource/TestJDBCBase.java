/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.jdbcSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqError;
import io.vantiq.client.VantiqResponse;
import org.junit.BeforeClass;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class TestJDBCBase {

    public static final String JDBC_SRC_TYPE = "JDBC";
    public static final String JDBC_IMPL_DEF = "jdbcImpl.json";
    private static final Integer JDBC_IMPL_MAX_SIZE = 1000;

    static final String VANTIQ_SOURCE_IMPL = "system.sourceimpls";
    static String testDBUsername;
    static String testDBPassword;
    static String testDBURL;
    static String testAuthToken;
    static String testVantiqServer;
    static String jdbcDriverLoc;
    static String testSourceName;
    static String testSourceNameAsynch;
    static String testTypeName;
    static String testProcedureName;
    static String testRuleName;
    static String testTopicName;
    
    @BeforeClass
    public static void getProps() {
        testDBUsername = System.getProperty("EntConJDBCUsername", null);
        testDBPassword = System.getProperty("EntConJDBCPassword", null);
        testDBURL = System.getProperty("EntConJDBCURL", null);
        testAuthToken = System.getProperty("TestAuthToken", null);
        testVantiqServer = System.getProperty("TestVantiqServer", null);
        jdbcDriverLoc = System.getenv("JDBC_DRIVER_LOC");
        testSourceName = System.getProperty("EntConTestSourceName", "testSourceName");
        testSourceNameAsynch = testSourceName + "_Asynch";
        testTypeName = System.getProperty("EntConTestTypeName", "testTypeName");
        testProcedureName = System.getProperty("EntConTestProcedureName", "testProcedureName");
        testRuleName = System.getProperty("EntConTestRuleName", "testRuleName");
        testTopicName = System.getProperty("EntConTestTopicName", "/test/topic/name");
    }

    private static boolean createdImpl = false;
    @SuppressWarnings("unchecked")
    protected static void createSourceImpl(Vantiq vantiq) throws Exception {
        VantiqResponse resp = vantiq.selectOne(VANTIQ_SOURCE_IMPL, JDBC_SRC_TYPE);
        if (!resp.isSuccess()) {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            byte[] implDef = new byte[JDBC_IMPL_MAX_SIZE];
            try (InputStream is = loader.getResourceAsStream(JDBC_IMPL_DEF))
            {
                assert is != null;
                int implSize = is.read(implDef);
                assert implSize > 0;
                assert implSize < JDBC_IMPL_MAX_SIZE;
            }

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> implMap = new LinkedHashMap<>();
            implMap = mapper.readValue(implDef, implMap.getClass());
            resp = vantiq.insert(VANTIQ_SOURCE_IMPL, implMap);
            assert resp.isSuccess();

            Map<String, String> where = new LinkedHashMap<>();
            where.put("name", "JDBC");
            VantiqResponse implResp = vantiq.select(VANTIQ_SOURCE_IMPL, null, where, null);
            assertFalse("Errors from fetching source impl", implResp.hasErrors());
            @SuppressWarnings({"rawtypes"})
            ArrayList responseBody = (ArrayList) implResp.getBody();
            assertEquals("Missing sourceImpl -- expected a count of 1", 1, responseBody.size());
            createdImpl = true;
        }
    }

    protected static void deleteSourceImpl(Vantiq vantiq) {
        if (createdImpl) {
            vantiq.deleteOne(VANTIQ_SOURCE_IMPL, JDBC_SRC_TYPE);
        }

        VantiqResponse resp = vantiq.selectOne(VANTIQ_SOURCE_IMPL, JDBC_SRC_TYPE);
        if (resp.hasErrors()) {
            List<VantiqError> errors = resp.getErrors();
            if (errors.size() != 1 || !errors.get(0).getCode().equals("io.vantiq.resource.not.found")) {
                fail("Error deleting source impl" + resp.getErrors());
            }
        } else if (createdImpl) {
            fail(JDBC_SRC_TYPE + " source impl found after deletion.");
        }
    }
}

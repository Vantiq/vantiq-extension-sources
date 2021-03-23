/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package io.vantiq.extjsdk;

import org.junit.BeforeClass;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqResponse;

public class RoundTripTestBase {
    public static final String UNUSED = "unused";

    public static String testAuthToken = null;
    public static String testVantiqServer = null;
    public static String testSourceName = null;
    public static String testTypeName = null;
    public static String testRuleName = null;
    public static final String TEST_IMPL_NAME = "TEST_SOURCE_IMPL";

    static Vantiq vantiq;
    
    @BeforeClass
    public static void getProps() {
        testAuthToken = System.getProperty("TestAuthToken", null);
        testVantiqServer = System.getProperty("TestVantiqServer", null);
        testSourceName = System.getProperty("EntConTestSourceName", "testSourceName");
        testTypeName = System.getProperty("EntConTestTypeName", "testTypeName");
        testRuleName = System.getProperty("EntConTestRuleName", "testRuleName");
        assumeTrue("Tests require system property 'buildDir' to be set -- should be extjsdk/build",
                System.getProperty("buildDir") != null);

        vantiq = new io.vantiq.client.Vantiq(testVantiqServer);
        vantiq.setAccessToken(testAuthToken);

    }
    
    public static final String SOURCE_NAME = "testSourceName";

    static final String NOT_FOUND_CODE = "io.vantiq.resource.not.found";
    static final int WAIT_FOR_ASYNC_MILLIS = 5000;

    public static boolean checkSourceExists() {
        Map<String, String> where = new LinkedHashMap<String, String>();
        where.put("name", SOURCE_NAME);
        VantiqResponse response = vantiq.select("system.sources", null, where, null);
        ArrayList responseBody = (ArrayList) response.getBody();
        if (responseBody.isEmpty()) {
            return false;
        } else {
            return true;
        }
    }

    public static void deleteSource() {
        Map<String, Object> where = new LinkedHashMap<String, Object>();
        where.put("name", SOURCE_NAME);
        VantiqResponse response = vantiq.delete("system.sources", where);
    }

    public static void deleteType() {
        Map<String, Object> where = new LinkedHashMap<String, Object>();
        where.put("name", testTypeName);
        VantiqResponse response = vantiq.delete("system.types", where);
    }

    public static boolean checkRuleExists() {
        Map<String, String> where = new LinkedHashMap<String, String>();
        where.put("name", testRuleName);
        VantiqResponse response = vantiq.select("system.rules", null, where, null);
        ArrayList responseBody = (ArrayList) response.getBody();
        if (responseBody.isEmpty()) {
            return false;
        } else {
            return true;
        }
    }

    public static void deleteRule() {
        Map<String, Object> where = new LinkedHashMap<String, Object>();
        where.put("name", testRuleName);
        VantiqResponse response = vantiq.delete("system.rules", where);
    }

    public static void setupSource(Map<String,Object> sourceDef) {
        VantiqResponse insertResponse = vantiq.insert("system.sources", sourceDef);
    }
    
    public static void setupSourceImpl() {
        Map<String,String> where = new LinkedHashMap<String,String>();
        where.put("name", TEST_IMPL_NAME);
        VantiqResponse implResp = vantiq.select("system.sourceimpls", null, where, null);
        if (((List) implResp.getBody()).size() == 0) {
            Map<String, Object> srcImpl = new LinkedHashMap<>();
            srcImpl.put("name", TEST_IMPL_NAME);
            srcImpl.put("baseType", "EXTENSION");
            srcImpl.put("verticle", "service:extensionSource");
            srcImpl.put("config", new LinkedHashMap());
            implResp = vantiq.insert("system.sourceimpls", srcImpl);
            assertTrue("Failure to insert source impl", implResp.getStatusCode() == 200);
        }
    }

    public static void deleteSourceImpl() {
        Map<String, Object> where = new LinkedHashMap<String, Object>();
        where.put("name", TEST_IMPL_NAME);
        VantiqResponse response = vantiq.delete("system.sourceimpls", where);
    }


    public static Map<String,Object> createSource() {
        Map<String,Object> sourceDef = new LinkedHashMap<String,Object>();
 
        sourceDef.put("name", SOURCE_NAME);
        sourceDef.put("type", TEST_IMPL_NAME);
        sourceDef.put("active", "true");
        sourceDef.put("direction", "BOTH");
        sourceDef.put("config", new LinkedHashMap());

        return sourceDef;
    }

    public static void setupType() {
        Map<String,Object> typeDef = new LinkedHashMap<String,Object>();
        Map<String,Object> properties = new LinkedHashMap<String,Object>();
        Map<String,Object> propertyDef = new LinkedHashMap<String,Object>();
        propertyDef.put("type", "Integer");
        propertyDef.put("required", true);
        properties.put("msgId", propertyDef);
        typeDef.put("properties", properties);
        typeDef.put("name", testTypeName);
        vantiq.insert("system.types", typeDef);
    }

    public static void setupRule() {
        String rule = "RULE " + testRuleName + "\n"
                + "WHEN EVENT OCCURS ON \"/sources/" + testSourceName + " AS sourceEvent\n"
                + "var message = sourceEvent.value\n"
                + "INSERT " + testTypeName + "(msgId: message.msgId)";

        vantiq.insert("system.rules", rule);
    }

}

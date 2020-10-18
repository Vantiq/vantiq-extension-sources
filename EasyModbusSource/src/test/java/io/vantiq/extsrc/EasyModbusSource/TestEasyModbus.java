/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.EasyModbusSource;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqResponse;
import io.vantiq.extsrc.EasyModbusSource.exception.VantiqEasymodbusException;

public class TestEasyModbus extends TestEasyModbusBase {

    // Queries to be tested
    static final String SELECT_COILS = "SELECT * from coils";
    static final String SELECT_HOLDINGREGISTERS = "SELECT * from holdingregisters";

    // Queries to test errors
    static final String NO_TABLE = "SELECT * FROM jibberish";
    static final String NO_FIELD = "SELECT jibberish FROM Test";
    static final String NO_FIELD_ITEM_INDEX = "SELECT itemrish FROM Test";
    static final String SYNTAX_ERROR = "ELECT * FROM Test";

    static final int CORE_START_TIMEOUT = 10;

    static EasyModbusCore core;
    static EasyModbus easyModbus;
    static Vantiq vantiq;

    @Before
    public void setup() {
        assumeTrue("Simulation is not running", TestEasyModbusBase.isSimulationRunning());
        easyModbus = new EasyModbus();
        vantiq = new io.vantiq.client.Vantiq(testVantiqServer);
        vantiq.setAccessToken(testAuthToken);
    }

    @Test
    public void testProcessCoilsQuery() throws VantiqEasymodbusException {
        assumeTrue(testIPAddress != null && testIPPort != 0);
        easyModbus.setupEasyModbus(testIPAddress, testIPPort, false, 0);

        HashMap[] queryResult;

        Map<String, Object> request = CreateFalseCoilsRequest();
        easyModbus.hanldeUpdateCommand(request);

        // Try processQuery with a nonsense query
        try {
            queryResult = easyModbus.processQuery("jibberish");
            fail("Should have thrown exception.");
        } catch (VantiqEasymodbusException e) {
            // Expected behavior
        }

        // Select the row that we previously inserted
        try {
            queryResult = easyModbus.processQuery(SELECT_COILS);
            assert (Integer) queryResult[0].size() == 1;
            ArrayList<Value> list = (ArrayList<Value>) queryResult[0].get("values");
            assert (Integer) list.size() == 20;
            for (int i = 0; i < list.size(); i++) {
                Value v = list.get(i);
                assert v.index == i;
                assertFalse("illegal value on Index : " + v.index, v.value); // all fields should be false.
            }

            request = SetValue(request, 0, true);
            int rc = easyModbus.hanldeUpdateCommand(request);
            assert rc == 0;

        } catch (VantiqEasymodbusException e) {
            fail("Should not throw an exception: " + e.getMessage());
        }

        // Try selecting again, should return empty HashMap Array since row was deleted
        try {
            queryResult = easyModbus.processQuery(SELECT_COILS);
            assert (Integer) queryResult[0].size() == 1;
            ArrayList<Value> list = (ArrayList<Value>) queryResult[0].get("values");
            assert (Integer) list.size() == 20;
            Value v1 = list.get(0);
            assertTrue("illegal value on Index : " + v1.index, v1.value); // all fields should be false.

            for (int i = 1; i < list.size(); i++) {
                Value v = list.get(i);
                assert v.index == i;
                assertFalse("illegal value on Index : " + v.index, v.value); // all fields should be false.
            }
        } catch (VantiqEasymodbusException e) {
            fail("Should not throw an exception: " + e.getMessage());
        }

        easyModbus.close();
    }

    @Test
    public void testProcessHoldingRegistersQuery() throws VantiqEasymodbusException {
        assumeTrue(testIPAddress != null && testIPPort != 0);
        easyModbus.setupEasyModbus(testIPAddress, testIPPort, false, 0);

        HashMap[] queryResult;

        Map<String, Object> request = CreateResetRegistersRequest();
        easyModbus.hanldeUpdateCommand(request);

        // Select the row that we previously inserted
        try {
            queryResult = easyModbus.processQuery(SELECT_HOLDINGREGISTERS);
            assert (Integer) queryResult[0].size() == 1;
            ArrayList<Register> list = (ArrayList<Register>) queryResult[0].get("registers");
            assert (Integer) list.size() == 20;
            for (int i = 0; i < list.size(); i++) {
                Register v = list.get(i);
                assert v.index == i;
                assertTrue("illegal value on Index : " + v.index, v.value == 0); // all fields should be false.
            }

            request = SetValue(request, 0, 1);
            int rc = easyModbus.hanldeUpdateCommand(request);
            assert rc == 0;

        } catch (VantiqEasymodbusException e) {
            fail("Should not throw an exception: " + e.getMessage());
        }

        // Try selecting again, should return empty HashMap Array since row was deleted
        try {
            queryResult = easyModbus.processQuery(SELECT_HOLDINGREGISTERS);
            assert (Integer) queryResult[0].size() == 1;
            ArrayList<Register> list = (ArrayList<Register>) queryResult[0].get("registers");
            assert (Integer) list.size() == 20;
            Register v1 = list.get(0);
            assertTrue("illegal value on Index : " + v1.index, v1.value == 1); // all fields should be false.

            for (int i = 1; i < list.size(); i++) {
                Register v = list.get(i);
                assert v.index == i;
                assertTrue("illegal value on Index : " + v.index, v.value == 0); // all fields should be false.
            }
        } catch (VantiqEasymodbusException e) {
            fail("Should not throw an exception: " + e.getMessage());
        }

        easyModbus.close();
    }

    @Test
    public void testCorrectErrors() throws VantiqEasymodbusException {
        assumeTrue(testIPAddress != null && testIPPort != 0);
        easyModbus.setupEasyModbus(testIPAddress, testIPPort, false, 0);
        HashMap[] queryResult;
        int publishResult;

        // Check error code for selecting from non-existent table
        try {
            queryResult = easyModbus.processQuery(NO_TABLE);
            fail("Should have thrown an exception.");
        } catch (VantiqEasymodbusException e) {
            String message = e.getMessage();
            assert message.contains("1006");
        }

        // Check error code for selecting non-existent field
        try {
            queryResult = easyModbus.processQuery(NO_FIELD);
            fail("Should have thrown an exception.");
        } catch (VantiqEasymodbusException e) {
            String message = e.getMessage();
            assert message.contains("1007");
        }

        try {
            queryResult = easyModbus.processQuery(NO_FIELD_ITEM_INDEX);
            fail("Should have thrown an exception.");
        } catch (VantiqEasymodbusException e) {
            String message = e.getMessage();
            assert message.contains("1004");
        }

        // Check error code for syntax error
        try {
            queryResult = easyModbus.processQuery(SYNTAX_ERROR);
            fail("Should have thrown an exception.");
        } catch (VantiqEasymodbusException e) {
            String message = e.getMessage();
            assert message.contains("1005");
        }

    }

    // ================================================= Helper functions
    private Map<String, Object> CreateFalseCoilsRequest() {
        Map<String, Object> request = new HashMap<String, Object>();
        Map<String, Object> b = new HashMap<String, Object>();
        ArrayList<Map<String, Object>> l = new ArrayList<Map<String, Object>>(); // request.get("body");
        request.put("type", "coils");
        request.put("body", l);

        ArrayList<HashMap<String, Object>> n = new ArrayList<HashMap<String, Object>>();
        for (int i = 0; i < 20; i++) {
            HashMap<String, Object> m = new HashMap<String, Object>();
            m.put("value", false);
            n.add(m);
        }

        b.put("values", n);
        l.add(b);
        return request;
    }

    private Map<String, Object> CreateResetRegistersRequest() {
        Map<String, Object> request = new HashMap<String, Object>();
        Map<String, Object> b = new HashMap<String, Object>();
        ArrayList<Map<String, Object>> l = new ArrayList<Map<String, Object>>(); // request.get("body");
        request.put("type", "holdingregisters");
        request.put("body", l);

        ArrayList<HashMap<String, Object>> n = new ArrayList<HashMap<String, Object>>();
        for (int i = 0; i < 20; i++) {
            HashMap<String, Object> m = new HashMap<String, Object>();
            m.put("value", 0);
            n.add(m);
        }

        b.put("registers", n);
        l.add(b);
        return request;
    }

    private Map<String, Object> SetValue(Map<String, Object> request, int index, boolean value) {
        request.put("type", "coils");
        ArrayList<Map<String, Object>> l = (ArrayList<Map<String, Object>>) request.get("body");
        Map<String, Object> b = l.get(0);

        ArrayList<HashMap<String, Object>> n = (ArrayList<HashMap<String, Object>>) b.get("values");
        HashMap<String, Object> m = n.get(index);
        m.put("value", value);
        return request;
    }

    private Map<String, Object> SetValue(Map<String, Object> request, int index, Integer value) {
        request.put("type", "holdingregisters");
        ArrayList<Map<String, Object>> l = (ArrayList<Map<String, Object>>) request.get("body");
        Map<String, Object> b = l.get(0);

        ArrayList<HashMap<String, Object>> n = (ArrayList<HashMap<String, Object>>) b.get("registers");
        HashMap<String, Object> m = n.get(index);
        m.put("value", value);
        return request;
    }
    // =================================================
}
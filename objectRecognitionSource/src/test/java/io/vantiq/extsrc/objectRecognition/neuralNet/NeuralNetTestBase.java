
/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extsrc.objectRecognition.neuralNet;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqError;
import io.vantiq.client.VantiqResponse;
import io.vantiq.extsrc.objectRecognition.ObjRecTestBase;
import io.vantiq.extsrc.objectRecognition.ObjectRecognitionCore;

public class NeuralNetTestBase extends ObjRecTestBase {
    public static final String MODEL_DIRECTORY = System.getProperty("buildDir") + "/models";
    public static final String SOURCE_NAME = "testSourceName";

    static final String NOT_FOUND_CODE = "io.vantiq.resource.not.found";
    static final int WAIT_FOR_ASYNC_MILLIS = 5000;

    public static byte[] getTestImage() {
        File image = new File(JPEG_IMAGE_LOCATION);
        try {
            return Files.readAllBytes(image.toPath());
        } catch (IOException e) {
            fail("Could not read testImage");
            return null;
        }
    }

    public static void checkUploadToVantiq(String name, Vantiq vantiq) throws InterruptedException {
        boolean done = false;
        int retries = 0;
        int maxRetries = WAIT_FOR_ASYNC_MILLIS / 50;
        while (!done) {
            done = true;
            VantiqResponse vantiqResponse = vantiq.selectOne("system.documents", name);
            if (vantiqResponse.hasErrors()) {
                if (++retries < maxRetries) {
                    done = false;
                    Thread.sleep(50);
                } else {
                    List<VantiqError> errors = vantiqResponse.getErrors();
                    for (int i = 0; i < errors.size(); i++) {
                        if (errors.get(i).getCode().equals(NOT_FOUND_CODE)) {
                            fail("Image should have been uploaded to VANTIQ");
                        }
                    }
                }
            }
        }
    }

    public static void checkNotUploadToVantiq(String name, Vantiq vantiq) throws InterruptedException {
        boolean done = false;
        int retries = 0;
        int maxRetries = WAIT_FOR_ASYNC_MILLIS / 50;
        while (!done) {
            done = true;
            VantiqResponse vantiqResponse = vantiq.selectOne("system.documents", name);
            if (vantiqResponse.isSuccess()) {
                if (++retries < maxRetries) {
                    done = false;
                    Thread.sleep(50);
                } else {
                    fail("Image should not have been uploaded to VANTIQ");
                }
            }
        }
    }

    public static boolean checkSourceExists(Vantiq vantiq) {
        Map<String,String> where = new LinkedHashMap<String,String>();
        where.put("name", testSourceName);
        VantiqResponse response = vantiq.select("system.sources", null, where, null);
        ArrayList responseBody = (ArrayList) response.getBody();
        if (responseBody.isEmpty()) {
            return false;
        } else {
            return true;
        }
    }

    public static void deleteSource(Vantiq vantiq) {
        Map<String,Object> where = new LinkedHashMap<String,Object>();
        where.put("name", testSourceName);
        VantiqResponse response = vantiq.delete("system.sources", where);
    }

    public static boolean checkTypeExists(Vantiq vantiq) {
        Map<String,String> where = new LinkedHashMap<String,String>();
        where.put("name", testTypeName);
        VantiqResponse response = vantiq.select("system.types", null, where, null);
        ArrayList responseBody = (ArrayList) response.getBody();
        if (responseBody.isEmpty()) {
            return false;
        } else {
            return true;
        }
    }

    public static void deleteType(Vantiq vantiq) {
        Map<String,Object> where = new LinkedHashMap<String,Object>();
        where.put("name", testTypeName);
        VantiqResponse response = vantiq.delete("system.types", where);
    }

    public static boolean checkRuleExists(Vantiq vantiq) {
        Map<String,String> where = new LinkedHashMap<String,String>();
        where.put("name", testRuleName);
        VantiqResponse response = vantiq.select("system.rules", null, where, null);
        ArrayList responseBody = (ArrayList) response.getBody();
        if (responseBody.isEmpty()) {
            return false;
        } else {
            return true;
        }
    }

    public static void deleteRule(Vantiq vantiq) {
        Map<String,Object> where = new LinkedHashMap<String,Object>();
        where.put("name", testRuleName);
        VantiqResponse response = vantiq.delete("system.rules", where);
    }
}

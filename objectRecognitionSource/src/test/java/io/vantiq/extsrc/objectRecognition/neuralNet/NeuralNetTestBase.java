
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
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vantiq.client.Vantiq;
import io.vantiq.client.VantiqError;
import io.vantiq.client.VantiqResponse;
import io.vantiq.extjsdk.Utils;
import io.vantiq.extsrc.objectRecognition.ObjRecTestBase;
import org.junit.BeforeClass;

@SuppressWarnings({"PMD.MutableStaticState"})
public class NeuralNetTestBase extends ObjRecTestBase {
    public static final String MODEL_DIRECTORY = System.getProperty("buildDir") + "/models";
    public static final String SOURCE_NAME = "testSourceName";
    public static final String OR_SRC_TYPE = "OBJECT_RECOGNITION";
    public static final String OR_IMPL_DEF = "objRecImpl.json";
    private static final Integer OR_IMPL_MAX_SIZE = 1000;

    static final String VANTIQ_DOCUMENTS = "system.documents";
    static final String VANTIQ_IMAGES = "system.images";
    static final String VANTIQ_SOURCE_IMPL = "system.sourceimpls";
    static final String NOT_FOUND_CODE = "io.vantiq.resource.not.found";
    static final int WAIT_FOR_ASYNC_MILLIS = 5000;

    @BeforeClass
    public static void setupConfigFile() {
        // Make initial Utils.obtainServerConfig() call so that we don't get errors later on
        try {
            createServerConfig();
        } catch (Exception e) {
            fail("Could not setup config file");
        }
    }

    // Note that we created the source impl so we can trash it iff we did.
    // Static so available in @BeforeClass/@AfterClass
    static boolean createdImpl = false;
    protected static File serverConfigFile;

    protected static void createServerConfig() throws Exception {
        // Make initial Utils.obtainServerConfig() call so that we don't get errors later on
        serverConfigFile = new File("server.config");
        serverConfigFile.createNewFile();
        serverConfigFile.deleteOnExit();
        Utils.obtainServerConfig();
    }

    @SuppressWarnings("unchecked")
    protected static void createSourceImpl(Vantiq vantiq) throws Exception {
        VantiqResponse resp = vantiq.selectOne(VANTIQ_SOURCE_IMPL, OR_SRC_TYPE);
        if (!resp.isSuccess()) {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            byte[] implDef = new byte[OR_IMPL_MAX_SIZE];
            try (InputStream is = loader.getResourceAsStream(OR_IMPL_DEF))
            {
                assert is != null;
                int implSize = is.read(implDef);
                assert implSize > 0;
                assert implSize < OR_IMPL_MAX_SIZE;
            }

            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> implMap = new LinkedHashMap<>();
            implMap = mapper.readValue(implDef, implMap.getClass());
            resp = vantiq.insert(VANTIQ_SOURCE_IMPL, implMap);
            assert resp.isSuccess();
            createdImpl = true;
        }
    }

    protected static void deleteSourceImpl(Vantiq vantiq) throws Exception {
        if (createdImpl) {
            vantiq.deleteOne(VANTIQ_SOURCE_IMPL, OR_SRC_TYPE);
        }

        VantiqResponse resp = vantiq.selectOne(VANTIQ_SOURCE_IMPL, OR_SRC_TYPE);
        if (resp.hasErrors()) {
            List<VantiqError> errors = resp.getErrors();
            if (errors.size() != 1 || !errors.get(0).getCode().equals("io.vantiq.resource.not.found")) {
                fail("Error deleting source impl" + resp.getErrors());
            }
        } else if (createdImpl) {
            fail(OR_SRC_TYPE + " source impl found after deletion.");
        }
        createdImpl = false;
    }

    public static byte[] getTestImage() {
        File image = new File(JPEG_IMAGE_LOCATION);
        try {
            return Files.readAllBytes(image.toPath());
        } catch (IOException e) {
            fail("Could not read testImage");
            return null;
        }
    }

    public static void checkUploadToVantiq(String name, Vantiq vantiq, String resourceName) throws InterruptedException {
        boolean done = false;
        int retries = 0;
        int maxRetries = WAIT_FOR_ASYNC_MILLIS / 50;
        while (!done) {
            VantiqResponse vantiqResponse = vantiq.selectOne(resourceName, name);
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
            } else if (vantiqResponse.isSuccess()) {
                done = true;
            } else {
                retries += 1;
                Thread.sleep(50);
            }
        }
    }

    public static void checkNotUploadToVantiq(String name, Vantiq vantiq, String resourceName) throws InterruptedException {
        boolean done = false;
        int retries = 0;
        int maxRetries = WAIT_FOR_ASYNC_MILLIS / 50;
        while (!done) {
            done = true;
            VantiqResponse vantiqResponse = vantiq.selectOne(resourceName, name);
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

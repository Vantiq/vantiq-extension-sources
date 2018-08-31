
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

import io.vantiq.extsrc.objectRecognition.ObjRecTestBase;

public class NeuralNetTestBase extends ObjRecTestBase {
    public static final String MODEL_DIRECTORY = "src/test/resources/models";

    public static byte[] getTestImage() {
        File image = new File(IMAGE_LOCATION);
        try {
            return Files.readAllBytes(image.toPath());
        } catch (IOException e) {
            fail("Could not read testImage");
            return null;
        }
    }
}

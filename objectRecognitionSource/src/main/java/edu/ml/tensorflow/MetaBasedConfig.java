/*
 * Copyright (c) 2018 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */

package edu.ml.tensorflow;

import static edu.ml.tensorflow.Config.FRAME_SIZE;
import static edu.ml.tensorflow.Config.MEAN;

public class MetaBasedConfig {
    // General config values for YOLO Processor
    public int frameSize;
    public float mean;
    
    // Flag to decide if we should use .meta frame size, or default Config frame size
    public boolean useMetaIfAvailable;
    
    public MetaBasedConfig() {
        // Check if user has manually changed the default FRAME_SIZE in Config
        if (FRAME_SIZE == 416) {
            useMetaIfAvailable = true;
        } else {
            useMetaIfAvailable = false;
        }
        
        frameSize = FRAME_SIZE;
        mean = MEAN;
    }
}

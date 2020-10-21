/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */
package io.vantiq.extsrc.EasyModbusSource;

/**
 * Helper class for convertion between Vantiq and EasyModbus
 */
public class Value {
    public Value(int index, boolean value) {
        this.index = index;
        this.value = value;
    }

    public int index;
    public boolean value;
}

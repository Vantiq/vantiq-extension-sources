/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */
package io.vantiq.extsrc.EasyModbusSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * convertion between the Java object as accepted from Vantiq and the structure
 * as expected by the EasyModbus SDK .
 */
public class InputRegisters {
    ArrayList<Register> registers = new ArrayList<Register>(); // Map.of();// new Map<Integer,String>();

    public InputRegisters Set(int[] rr, int index) {
        for (int i = 0; i < rr.length; i++) {
            registers.add(new Register(i + index, rr[i]));
        }
        return this;
    }

    public HashMap[] Get() {
        Map<String, Object>[] r = new HashMap[1];
        r[0] = new HashMap<String, Object>() {
            {
                put("registers", registers);
            }
        };
        return (HashMap[]) r;
    }
}
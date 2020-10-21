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
 * convertion between the Java object as accepted from Vantiq and the structure as expected
 * by the EasyModbus SDK .
 */
public class InputDiscrete {
    ArrayList<Value>  values = new ArrayList<Value>() ; 

    public InputDiscrete set(boolean[] rr,int index) {
            for (int i = 0; i < rr.length; i++)
            {
                values.add(new Value(i+index , rr[i] ));
            }
            return this;
        }

        public HashMap[] get()  {
            Map<String,Object >[]r=new HashMap[1];
            r[0] = new HashMap<String,Object >(){{
                put("values",values);}};
            return (HashMap[]) r;
        }
}
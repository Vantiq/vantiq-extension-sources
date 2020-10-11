package io.vantiq.extsrc.EasyModbusSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
public class InputRegisters {
    ArrayList<Register>  registers = new ArrayList<Register>() ; //Map.of();// new Map<Integer,String>();

    public InputRegisters Set(int[] rr,int index) {
            for (int i = 0; i < rr.length; i++) {
                registers.add(new Register(i+index , rr[i] ));
            }
            return this;
        }

    public HashMap[] Get()    {
        Map<String,Object >[]r=new HashMap[1];
        r[0] = new HashMap<String,Object >(){{
            put("registers",registers);}};
        return (HashMap[]) r;
    }


}
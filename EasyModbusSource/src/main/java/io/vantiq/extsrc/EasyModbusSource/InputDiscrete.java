package io.vantiq.extsrc.EasyModbusSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
public class InputDiscrete {
    ArrayList<Value>  values = new ArrayList<Value>() ; 

    public InputDiscrete Set(boolean[] rr,int index) {
            for (int i = 0; i < rr.length; i++)
            {
                values.add(new Value(i+index , rr[i] ));
            }
            return this;
        }

        public HashMap[] Get()  {
            Map<String,Object >[]r=new HashMap[1];
            r[0] = new HashMap<String,Object >(){{
                put("values",values);}};
            return (HashMap[]) r;
        }
}
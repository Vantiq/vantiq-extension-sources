package io.vantiq.extsrc.EasyModbusSource;

public class Value
{
    public Value(int index , boolean value)
    {
        this.index = index;
        this.value = value; 
    }
    public int index ;//{ get; set; }
    public boolean value ;//{ get; set; }
};


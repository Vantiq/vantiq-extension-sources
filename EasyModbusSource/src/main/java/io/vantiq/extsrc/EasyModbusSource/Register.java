package io.vantiq.extsrc.EasyModbusSource;

public class Register
{
    public Register(int index , int value)
    {
        this.index = index;
        this.value = value; 
    }
    public int index ;//{ get; set; }
    public int value ;//{ get; set; }
};


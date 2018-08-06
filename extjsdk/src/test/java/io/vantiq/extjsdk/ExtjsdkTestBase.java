package io.vantiq.extjsdk;

//Author: Alex Blumer
//Email: alex.j.blumer@gmail.com

import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ExtjsdkTestBase {

    
    int WAIT_PERIOD = 10; // Milliseconds to wait between checks on async actions
    public void waitUntilTrue(int msTimeout, Supplier<Boolean> condition) {
        for (int i = 0; i < msTimeout / WAIT_PERIOD; i++) {
            if (condition.get() == true) {
                return;
            }
            
            try {
                Thread.sleep(WAIT_PERIOD);
            } catch (InterruptedException e) {}
        }
    }

    public void print(String str) {
        System.out.println(str);
    }
    public ObjectMapper mapper = new ObjectMapper();
    
}

package io.vantiq.extsrc.udp;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import io.vantiq.extjsdk.ExtjsdkTestBase;

public class UDPTestBase extends ExtjsdkTestBase {
    public static boolean socketCanBind(int port, InetAddress address) {
        DatagramSocket s;
        
        try {
            s = new DatagramSocket(port, address);
            
        } catch (SocketException e) {
            return false;
        }
        
        s.close();
        return true;
    }
}

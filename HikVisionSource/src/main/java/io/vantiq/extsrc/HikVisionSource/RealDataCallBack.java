package io.vantiq.extsrc.HikVisionSource;


import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.ByteByReference;

public class RealDataCallBack implements  HCNetSDK.FRealDataCallBack_V30  {
    public void invoke(NativeLong lRealHandle, int dwDataType,
            ByteByReference pBuffer, int dwBufSize, Pointer pUser)
            {
                System.out.println("Receive notification on class RealDataCallBack");

            }
}
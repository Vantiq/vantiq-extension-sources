/*
 * Copyright (c) 2020 Vantiq, Inc.
 *
 * All rights reserved.
 *
 * SPDX: MIT
 */
package io.vantiq.extsrc.HikVisionSource;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.ByteByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RealDataCallBack implements HCNetSDK.FRealDataCallBack_V30 {

    Logger log = LoggerFactory.getLogger(this.getClass().getCanonicalName());

    public void invoke(NativeLong lRealHandle, int dwDataType, ByteByReference pBuffer, int dwBufSize, Pointer pUser) {
        log.error("Receive notification on class RealDataCallBack");

    }
}
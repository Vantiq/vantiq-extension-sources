
/*
 * Copyright (c) 2021 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extjsdk;

//Author: Alex Blumer, Namir Fawaz, Fred Carter
//Email: support@vantiq.com


import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import okhttp3.Request;
import okhttp3.WebSocket;
import okio.Buffer;
import okio.BufferedSink;
import okio.ByteString;
import okio.Source;
import okio.Timeout;

/**
 * The WebSocket used by {@link FalseClient}. The data stored here can be accessed through 
 * {@link FalseClient}.getMessageAs*().
 */
public class FalseWebSocket implements WebSocket {
    FalseBufferedSink s = new FalseBufferedSink();
    
    public byte[] getMessage() {
        return s.retrieveSentBytes();
    }
    
    @Override
    public boolean send(ByteString bytes) {
        s.write(bytes);
        return true;
    }

    //================================ Necessary to implement WebSocket ================================

    @Override
    public boolean close(int code, String reason) {return true;}
    @Override
    public void cancel() {}
    @Override
    public long queueSize() {return 0;}
    @Override
    public Request request() {return null;}
    @Override
    public boolean send(String s) {return false;}

    // Buffered Sink implementation
    public class FalseBufferedSink implements BufferedSink {
        ByteString savedBytes = null;
        
        public byte[] retrieveSentBytes() {
            return savedBytes != null ? savedBytes.toByteArray() : null;
        }
        
        // Called by FalseWebSocket.send()
        @Override
        public BufferedSink write(ByteString source) {
            savedBytes = source;
            return null;
        }

        //================================ Necessary to implement BufferedSink ================================
        @Override
        public void write(Buffer source, long byteCount) {}
        @Override
        public Timeout timeout() {return null;}
        @Override
        public boolean isOpen() {return false;}
        @Override
        public void close() {}
        @Override
        public Buffer buffer() {return null;}
        @Override
        public BufferedSink write(ByteString byteString, int offset, int byteCount) {return null;}
        @Override
        public BufferedSink write(byte[] source) {return null;}
        @Override
        public long writeAll(Source source) {return 0;}
        @Override
        public BufferedSink write(Source source, long byteCount) {return null;}
        @Override
        public BufferedSink write(byte[] bytes, int i, int i1) {return null;}
        @Override
        public int write(ByteBuffer src) {return 0;}
        @Override
        public BufferedSink writeUtf8(String string) {return null;}
        @Override
        public BufferedSink writeUtf8(String string, int beginIndex, int endIndex) {return null;}
        @Override
        public BufferedSink writeUtf8CodePoint(int codePoint)  {return null;}
        @Override
        public BufferedSink writeString(String string, Charset charset)  {return null;}
        @Override
        public BufferedSink writeString(String string, int beginIndex, int endIndex, Charset charset) {return null;}
        @Override
        public BufferedSink writeByte(int b) {return null;}
        @Override
        public BufferedSink writeShort(int s) {return null;}
        @Override
        public BufferedSink writeShortLe(int s) {return null;}
        @Override
        public BufferedSink writeInt(int i) {return null;}
        @Override
        public BufferedSink writeIntLe(int i) {return null;}
        @Override
        public BufferedSink writeLong(long v) {return null;}
        @Override
        public BufferedSink writeLongLe(long v) {return null;}
        @Override
        public BufferedSink writeDecimalLong(long v) {return null;}
        @Override
        public BufferedSink writeHexadecimalUnsignedLong(long v) {return null;}
        @Override
        public void flush() {}
        @Override
        public BufferedSink emit() {return null;}
        @Override
        public BufferedSink emitCompleteSegments() {return null;}
        @Override
        public OutputStream outputStream() {return null;}
        @Override
        public Buffer getBuffer() {return null;}
    }
}



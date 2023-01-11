
/*
 * Copyright (c) 2021 Vantiq, Inc.
 *
 * All rights reserved.
 * 
 * SPDX: MIT
 */

package io.vantiq.extjsdk;

//Authors: Alex Blumer, Namir Fawaz, Fred Carter
//Email: support@vantiq.com

import java.io.ByteArrayOutputStream;
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
import org.jetbrains.annotations.NotNull;

/**
 * The WebSocket used by {@link FalseClient}. The data stored here can be accessed through 
 * {@link FalseClient}.getMessageAs*().
 */
public class FalseWebSocket implements WebSocket {
    FalseBufferedSink s = new FalseBufferedSink();
    boolean closedByRemote = false;
    
    public byte[] getMessage() {
        return s.retrieveSentBytes();
    }
    
    public void setClosedByRemote(boolean newState) {
        closedByRemote = newState;
    }
    @Override
    public boolean send(@NotNull ByteString bytes) {
        if (closedByRemote) {
            throw new IllegalStateException("Emulating remote closure or other unexpected state");
        }
        s.write(bytes);
        return true;
    }

    //================================ Necessary to implement WebSocket ================================

    @Override
    public boolean close(int code, String reason) {
        return true;
    }

    @Override
    public void cancel() {

    }

    @Override
    public long queueSize() {
        return 0;
    }

    @NotNull
    @Override
    public Request request() {
        return new Request.Builder().build();
    }

    @Override
    public boolean send(@NotNull String s) {
        return false;
    }

    // Buffered Sink implementation
    public static class FalseBufferedSink implements BufferedSink {
        ByteString savedBytes = null;
        
        public byte[] retrieveSentBytes() {
            return savedBytes != null ? savedBytes.toByteArray() : null;
        }
        
        // Called by FalseWebSocket.send()
        @NotNull
        @Override
        public BufferedSink write(@NotNull ByteString source) {
            savedBytes = source;
            return new FalseBufferedSink();
        }

        //================================ Necessary to implement BufferedSink ================================
        @Override
        public void write(@NotNull Buffer source, long byteCount) {

        }

        @NotNull
        @Override
        public Timeout timeout() {
            return new Timeout();
        }

        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public void close() {

        }

        @NotNull
        @Override
        public Buffer buffer() {
            return new Buffer();
        }

        @NotNull
        @Override
        public BufferedSink write(@NotNull ByteString byteString, int offset, int byteCount) {
            return new FalseBufferedSink();
        }

        @NotNull
        @Override
        public BufferedSink write(@NotNull byte[] source) {
            return new FalseBufferedSink();
        }

        @Override
        public long writeAll(@NotNull Source source) {
            return 0;
        }

        @NotNull
        @Override
        public BufferedSink write(@NotNull Source source, long byteCount) {
            return new FalseBufferedSink();
        }

        @NotNull
        @Override
        public BufferedSink write(@NotNull byte[] bytes, int i, int i1) {
            return new FalseBufferedSink();
        }

        @Override
        public int write(ByteBuffer src) {
            return 0;
        }

        @NotNull
        @Override
        public BufferedSink writeUtf8(@NotNull String string) {
            return new FalseBufferedSink();
        }

        @NotNull
        @Override
        public BufferedSink writeUtf8(@NotNull String string, int beginIndex, int endIndex) {
            return new FalseBufferedSink();
        }

        @NotNull
        @Override
        public BufferedSink writeUtf8CodePoint(int codePoint) {
            return new FalseBufferedSink();
        }

        @NotNull
        @Override
        public BufferedSink writeString(@NotNull String string, @NotNull Charset charset) {
            return new FalseBufferedSink();
        }

        @NotNull
        @Override
        public BufferedSink writeString(@NotNull String string, int beginIndex, int endIndex, @NotNull Charset charset) {
            return new FalseBufferedSink();
        }

        @NotNull
        @Override
        public BufferedSink writeByte(int b) {
            return new FalseBufferedSink();
        }

        @NotNull
        @Override
        public BufferedSink writeShort(int s) {
            return new FalseBufferedSink();
        }

        @NotNull
        @Override
        public BufferedSink writeShortLe(int s) {
            return new FalseBufferedSink();
        }

        @NotNull
        @Override
        public BufferedSink writeInt(int i) {
            return new FalseBufferedSink();
        }

        @NotNull
        @Override
        public BufferedSink writeIntLe(int i) {
            return new FalseBufferedSink();
        }

        @NotNull
        @Override
        public BufferedSink writeLong(long v) {
            return new FalseBufferedSink();
        }

        @NotNull
        @Override
        public BufferedSink writeLongLe(long v) {
            return new FalseBufferedSink();
        }

        @NotNull
        @Override
        public BufferedSink writeDecimalLong(long v) {
            return new FalseBufferedSink();
        }

        @NotNull
        @Override
        public BufferedSink writeHexadecimalUnsignedLong(long v) {
            return new FalseBufferedSink();
        }

        @Override
        public void flush() {

        }

        @NotNull
        @Override
        public BufferedSink emit() {
            return new FalseBufferedSink();
        }

        @NotNull
        @Override
        public BufferedSink emitCompleteSegments() {
            return new FalseBufferedSink();
        }

        @NotNull
        @Override
        public OutputStream outputStream() {
            return new ByteArrayOutputStream();
        }

        @NotNull
        @Override
        public Buffer getBuffer() {
            return new Buffer();
        }
    }
}



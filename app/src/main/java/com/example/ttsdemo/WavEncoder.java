package com.example.ttsdemo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class WavEncoder {
    private WavEncoder() {}

    static byte[] pcm16(float[] samples, int sampleRate) {
        int dataSize = samples.length * 2;
        ByteBuffer out = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        out.put(new byte[]{'R','I','F','F'}).putInt(36 + dataSize);
        out.put(new byte[]{'W','A','V','E','f','m','t',' '}).putInt(16);
        out.putShort((short) 1).putShort((short) 1).putInt(sampleRate);
        out.putInt(sampleRate * 2).putShort((short) 2).putShort((short) 16);
        out.put(new byte[]{'d','a','t','a'}).putInt(dataSize);
        for (float sample : samples) {
            float value = Math.max(-1f, Math.min(1f, sample));
            out.putShort((short) Math.round(value * 32767f));
        }
        return out.array();
    }
}

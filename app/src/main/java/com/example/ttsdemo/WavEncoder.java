package com.example.ttsdemo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 工具类：手工拼装 WAV 文件头并把浮点 PCM 转成 16-bit 小端样本。 / Utility that hand-assembles the WAV file header and converts float PCM to 16-bit little-endian samples.
 */
final class WavEncoder {
    private WavEncoder() {}

    /** 将浮点单声道样本编码为含 44 字节头的完整 WAV 字节流。 / Encodes mono float samples into a complete WAV byte stream with a 44-byte header. */
    static byte[] pcm16(float[] samples, int sampleRate) {
        int dataSize = samples.length * 2;
        // WAV 规范要求小端字节序，须显式指定而非平台默认。 / WAV mandates little-endian; set it explicitly instead of relying on platform default.
        ByteBuffer out = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        out.put(new byte[]{'R','I','F','F'}).putInt(36 + dataSize);
        out.put(new byte[]{'W','A','V','E','f','m','t',' '}).putInt(16);
        out.putShort((short) 1).putShort((short) 1).putInt(sampleRate);
        out.putInt(sampleRate * 2).putShort((short) 2).putShort((short) 16);
        out.put(new byte[]{'d','a','t','a'}).putInt(dataSize);
        for (float sample : samples) {
            // 先夹取到 [-1,1]，超出范围的样本会溢出 int16 产生爆音。 / Clamp to [-1,1] first; out-of-range samples would overflow int16 and cause clipping noise.
            float value = Math.max(-1f, Math.min(1f, sample));
            out.putShort((short) Math.round(value * 32767f));
        }
        return out.array();
    }
}

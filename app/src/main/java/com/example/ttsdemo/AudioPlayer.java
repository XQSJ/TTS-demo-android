package com.example.ttsdemo;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * 用 AudioTrack（MODE_STATIC）整段播放合成出的单声道 PCM 音频，同一时刻只保留一条音轨。 / Plays synthesized mono PCM in full via AudioTrack (MODE_STATIC); only one track is kept alive at a time.
 */
final class AudioPlayer implements AutoCloseable {
    private AudioTrack current;

    /** 整段写入 PCM 并立即播放，写入不完整则释放并抛错。 / Writes the whole PCM buffer and starts playback; releases and throws if the write is incomplete. */
    synchronized void play(float[] samples, int sampleRate) {
        stop();
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                // 合成结果一次性全量写入，STATIC 免去流式喂食线程。 / The whole clip is uploaded at once; STATIC mode avoids a streaming feeder thread.
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(Math.multiplyExact(samples.length, Float.BYTES))
                .build();
        int written = track.write(samples, 0, samples.length, AudioTrack.WRITE_BLOCKING);
        if (written != samples.length) {
            // 静态模式写入不完整会导致尾音缺失，宁可失败也不静默截断。 / A partial static-mode write truncates the tail; fail loudly instead of silently clipping.
            track.release();
            throw new IllegalStateException("AudioTrack wrote " + written + "/" + samples.length);
        }
        current = track;
        track.play();
    }

    /** 停止并释放当前音轨，置空引用以便下次复用。 / Stops and releases the current track, clearing the reference for reuse. */
    synchronized void stop() {
        if (current == null) return;
        try {
            if (current.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) current.stop();
        } finally {
            current.release();
            current = null;
        }
    }

    /** AutoCloseable 释放入口，语义等同于 stop。 / AutoCloseable release entry, equivalent to stop. */
    @Override public void close() { stop(); }
}

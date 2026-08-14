package com.example.ttsdemo;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

final class AudioPlayer implements AutoCloseable {
    private AudioTrack current;

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
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(Math.multiplyExact(samples.length, Float.BYTES))
                .build();
        int written = track.write(samples, 0, samples.length, AudioTrack.WRITE_BLOCKING);
        if (written != samples.length) {
            track.release();
            throw new IllegalStateException("AudioTrack wrote " + written + "/" + samples.length);
        }
        current = track;
        track.play();
    }

    synchronized void stop() {
        if (current == null) return;
        try {
            if (current.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) current.stop();
        } finally {
            current.release();
            current = null;
        }
    }

    @Override public void close() { stop(); }
}

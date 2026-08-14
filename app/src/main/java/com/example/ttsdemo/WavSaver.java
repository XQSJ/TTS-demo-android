package com.example.ttsdemo;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.OutputStream;

final class WavSaver {
    private WavSaver() {}

    static Uri save(Context context, String fileName, float[] samples, int sampleRate) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TTSDemo");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("Cannot create WAV file");
        try (OutputStream output = context.getContentResolver().openOutputStream(uri, "w")) {
            if (output == null) throw new IllegalStateException("Cannot open WAV output");
            output.write(WavEncoder.pcm16(samples, sampleRate));
        } catch (Exception error) {
            context.getContentResolver().delete(uri, null, null);
            throw error;
        }
        ContentValues ready = new ContentValues();
        ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
        context.getContentResolver().update(uri, ready, null, null);
        return uri;
    }
}

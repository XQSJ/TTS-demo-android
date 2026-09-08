package com.example.ttsdemo;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.OutputStream;

/**
 * 工具类：经 MediaStore 把 WAV 写入 Downloads/TTSDemo，无需存储权限。 / Utility that writes WAV files to Downloads/TTSDemo via MediaStore, with no storage permission needed.
 */
final class WavSaver {
    private WavSaver() {}

    /** 创建 MediaStore 条目、写入 PCM 数据，失败时清理半成品，成功后清除 pending 标记并返回 Uri。 / Creates the MediaStore entry, writes PCM data, deletes the partial file on failure, clears the pending flag on success and returns the Uri. */
    static Uri save(Context context, String fileName, float[] samples, int sampleRate) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TTSDemo");
        // 先标记 pending，写入完成前对其他应用不可见。 / Mark pending first so the file stays invisible to other apps until fully written.
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("Cannot create WAV file");
        try (OutputStream output = context.getContentResolver().openOutputStream(uri, "w")) {
            if (output == null) throw new IllegalStateException("Cannot open WAV output");
            output.write(WavEncoder.pcm16(samples, sampleRate));
        } catch (Exception error) {
            // 写入中途失败必须删除条目，否则留下损坏文件污染 Downloads。 / A mid-write failure must delete the entry, otherwise a corrupt file lingers in Downloads.
            context.getContentResolver().delete(uri, null, null);
            throw error;
        }
        // 第二阶段：数据落盘后解除 pending，文件才对用户可见。 / Phase two: clear pending after data is flushed so the file becomes visible.
        ContentValues ready = new ContentValues();
        ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
        context.getContentResolver().update(uri, ready, null, null);
        return uri;
    }
}

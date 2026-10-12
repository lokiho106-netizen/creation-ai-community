package com.creationai.community.download;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public final class MediaStoreVideoDownloader {
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final long MAX_VIDEO_BYTES = 512L * 1024L * 1024L;

    public interface ProgressListener {
        void onProgress(long downloaded, long total);
    }

    private final Context context;

    public MediaStoreVideoDownloader(Context context) {
        this.context = context.getApplicationContext();
    }

    public Uri download(ResolvedVideo video, ProgressListener listener) throws IOException {
        UrlPolicy.requireMedia(video.mediaUrl);
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, displayName(video.sourceId));
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CreationAI");
            values.put(MediaStore.Video.Media.IS_PENDING, 1);
        }

        Uri outputUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        if (outputUri == null) throw new IOException("无法在系统媒体库创建视频");

        boolean complete = false;
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(video.mediaUrl).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11) CreationAI/0.1");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("视频下载失败：HTTP " + code);
            long total = connection.getContentLengthLong();
            if (total > MAX_VIDEO_BYTES) throw new IOException("视频超过 512 MB 安全上限");
            String contentType = connection.getContentType();
            if (contentType != null
                    && !contentType.toLowerCase(Locale.ROOT).startsWith("video/")
                    && !contentType.toLowerCase(Locale.ROOT).startsWith("application/octet-stream")) {
                throw new IOException("服务器返回的不是视频：" + contentType);
            }

            try (InputStream input = connection.getInputStream();
                 OutputStream output = resolver.openOutputStream(outputUri, "w")) {
                if (output == null) throw new IOException("无法写入系统媒体库");
                byte[] buffer = new byte[64 * 1024];
                long downloaded = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    downloaded += read;
                    if (downloaded > MAX_VIDEO_BYTES) throw new IOException("视频超过 512 MB 安全上限");
                    output.write(buffer, 0, read);
                    if (listener != null) listener.onProgress(downloaded, total);
                }
                output.flush();
            } finally {
                connection.disconnect();
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues ready = new ContentValues();
                ready.put(MediaStore.Video.Media.IS_PENDING, 0);
                resolver.update(outputUri, ready, null, null);
            }
            complete = true;
            return outputUri;
        } finally {
            if (!complete) resolver.delete(outputUri, null, null);
        }
    }

    private String displayName(String sourceId) {
        String safeId = sourceId == null ? "video" : sourceId.replaceAll("[^0-9A-Za-z_-]", "");
        if (safeId.isEmpty()) safeId = "video";
        return "CreationAI_" + safeId + "_" + System.currentTimeMillis() + ".mp4";
    }
}

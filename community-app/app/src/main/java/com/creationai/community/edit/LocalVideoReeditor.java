package com.creationai.community.edit;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.transformer.Composition;
import androidx.media3.transformer.EditedMediaItem;
import androidx.media3.transformer.ExportException;
import androidx.media3.transformer.ExportResult;
import androidx.media3.transformer.ProgressHolder;
import androidx.media3.transformer.Transformer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public final class LocalVideoReeditor {
    public interface Listener {
        void onProgress(int percent);
        void onCompleted(Uri outputUri);
        void onError(String message);
    }

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private Transformer transformer;
    private File outputFile;
    private Listener listener;

    public LocalVideoReeditor(Context context) {
        this.context = context.getApplicationContext();
    }

    public void start(Uri inputUri, long trimStartMs, long trimEndMs, Listener listener) {
        if (transformer != null) {
            listener.onError("已有一个视频正在处理");
            return;
        }
        this.listener = listener;
        ioExecutor.execute(() -> {
            try {
                long durationMs = readDuration(inputUri);
                long safeStart = Math.max(0, trimStartMs);
                long safeEnd = Math.max(0, trimEndMs);
                if (durationMs <= safeStart + safeEnd + 1_000) {
                    throw new IOException("视频太短，无法按当前片头片尾设置裁剪");
                }
                outputFile = new File(context.getCacheDir(),
                        "CreationAI_EDIT_" + System.currentTimeMillis() + ".mp4");
                mainHandler.post(() -> beginTransform(inputUri, safeStart, durationMs - safeEnd));
            } catch (Exception error) {
                mainHandler.post(() -> finishWithError(safeMessage(error)));
            }
        });
    }

    public void startByFrames(Uri inputUri, int trimStartFrames, int trimEndFrames,
                              Listener listener) {
        if (transformer != null) {
            listener.onError("已有一个视频正在处理");
            return;
        }
        this.listener = listener;
        ioExecutor.execute(() -> {
            try {
                VideoMetadata metadata = readMetadata(inputUri);
                long startMs = framesToDurationMs(trimStartFrames, metadata.framesPerSecond);
                long endMs = framesToDurationMs(trimEndFrames, metadata.framesPerSecond);
                if (metadata.durationMs <= startMs + endMs + 1_000) {
                    throw new IOException("视频太短，无法按当前首尾帧数裁剪");
                }
                outputFile = new File(context.getCacheDir(),
                        "CreationAI_EDIT_" + System.currentTimeMillis() + ".mp4");
                mainHandler.post(() -> beginTransform(inputUri, startMs,
                        metadata.durationMs - endMs));
            } catch (Exception error) {
                mainHandler.post(() -> finishWithError(safeMessage(error)));
            }
        });
    }

    public void cancel() {
        if (transformer != null) transformer.cancel();
        transformer = null;
        if (outputFile != null && outputFile.exists()) outputFile.delete();
    }

    public void release() {
        cancel();
        ioExecutor.shutdownNow();
    }

    private void beginTransform(Uri inputUri, long startMs, long endMs) {
        MediaItem input = new MediaItem.Builder()
                .setUri(inputUri)
                .setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build())
                .build();
        EditedMediaItem edited = new EditedMediaItem.Builder(input).build();
        transformer = new Transformer.Builder(context)
                .addListener(new Transformer.Listener() {
                    @Override
                    public void onCompleted(Composition composition, ExportResult exportResult) {
                        persistOutput();
                    }

                    @Override
                    public void onError(Composition composition, ExportResult exportResult,
                                        ExportException exportException) {
                        finishWithError("本地导出失败：" + safeMessage(exportException));
                    }
                })
                .build();
        transformer.start(edited, outputFile.getAbsolutePath());
        pollProgress();
    }

    private void pollProgress() {
        if (transformer == null) return;
        ProgressHolder holder = new ProgressHolder();
        int state = transformer.getProgress(holder);
        if (state == Transformer.PROGRESS_STATE_AVAILABLE && listener != null) {
            listener.onProgress(holder.progress);
        }
        if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
            mainHandler.postDelayed(this::pollProgress, 500);
        }
    }

    private long readDuration(Uri inputUri) throws IOException {
        return readMetadata(inputUri).durationMs;
    }

    private VideoMetadata readMetadata(Uri inputUri) throws IOException {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, inputUri);
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (duration == null) throw new IOException("无法读取视频时长");
            String frameRate = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);
            double framesPerSecond = 30d;
            if (frameRate != null && !frameRate.trim().isEmpty()) {
                try {
                    double parsed = Double.parseDouble(frameRate);
                    if (parsed >= 1d && parsed <= 240d) framesPerSecond = parsed;
                } catch (NumberFormatException ignored) {
                    // Some encoders omit or emit a non-numeric capture rate; 30 fps is the safe fallback.
                }
            }
            return new VideoMetadata(Long.parseLong(duration), framesPerSecond);
        } catch (NumberFormatException e) {
            throw new IOException("视频时长格式异常", e);
        } finally {
            retriever.release();
        }
    }

    static long framesToDurationMs(int frames, double framesPerSecond) {
        if (frames <= 0) return 0;
        double safeFrameRate = framesPerSecond >= 1d && framesPerSecond <= 240d
                ? framesPerSecond : 30d;
        return Math.max(1L, Math.round(frames * 1_000d / safeFrameRate));
    }

    private static final class VideoMetadata {
        final long durationMs;
        final double framesPerSecond;

        VideoMetadata(long durationMs, double framesPerSecond) {
            this.durationMs = durationMs;
            this.framesPerSecond = framesPerSecond;
        }
    }

    private void persistOutput() {
        transformer = null;
        ioExecutor.execute(() -> {
            Uri outputUri = null;
            try {
                ContentResolver resolver = context.getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.DISPLAY_NAME,
                        "CreationAI_EDIT_" + System.currentTimeMillis() + ".mp4");
                values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CreationAI");
                    values.put(MediaStore.Video.Media.IS_PENDING, 1);
                }
                outputUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                if (outputUri == null) throw new IOException("无法创建剪辑后媒体条目");
                try (FileInputStream input = new FileInputStream(outputFile);
                     OutputStream output = resolver.openOutputStream(outputUri, "w")) {
                    if (output == null) throw new IOException("无法写入剪辑后视频");
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues ready = new ContentValues();
                    ready.put(MediaStore.Video.Media.IS_PENDING, 0);
                    resolver.update(outputUri, ready, null, null);
                }
                Uri completed = outputUri;
                mainHandler.post(() -> {
                    if (listener != null) listener.onCompleted(completed);
                    cleanup();
                });
            } catch (Exception error) {
                if (outputUri != null) context.getContentResolver().delete(outputUri, null, null);
                mainHandler.post(() -> finishWithError("保存剪辑后视频失败：" + safeMessage(error)));
            }
        });
    }

    private void finishWithError(String message) {
        transformer = null;
        if (listener != null) listener.onError(message);
        cleanup();
    }

    private void cleanup() {
        if (outputFile != null && outputFile.exists()) outputFile.delete();
        outputFile = null;
        listener = null;
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName() : message;
    }
}

package com.creationai.community.ai;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class ContentPackageStore {
    private final Context context;

    public ContentPackageStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public File save(OriginalContentPackage value) throws IOException {
        File directory = new File(context.getFilesDir(), "content-packages");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("无法创建内容包目录");
        }
        File output = new File(directory,
                "original_" + value.generatedAtEpochMs + ".json");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            stream.write(value.toJson().toString(2).getBytes(StandardCharsets.UTF_8));
            stream.getFD().sync();
        } catch (Exception error) {
            if (output.exists()) output.delete();
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("内容包序列化失败", error);
        }
        return output;
    }
}

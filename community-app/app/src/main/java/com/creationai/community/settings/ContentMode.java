package com.creationai.community.settings;

public enum ContentMode {
    AUTHORIZED_REUSE("授权素材快速处理"),
    ORIGINAL_NEWS("资讯原创剪辑");

    public final String displayName;

    ContentMode(String displayName) {
        this.displayName = displayName;
    }

    public static ContentMode fromStored(String value) {
        if (value == null) return AUTHORIZED_REUSE;
        try {
            return ContentMode.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return AUTHORIZED_REUSE;
        }
    }
}

package com.creationai.community.download;

public final class ResolvedVideo {
    public final String sourceId;
    public final String title;
    public final String mediaUrl;
    public final int width;
    public final int height;
    public final long sizeBytes;

    public ResolvedVideo(String sourceId, String title, String mediaUrl,
                         int width, int height, long sizeBytes) {
        this.sourceId = sourceId;
        this.title = title;
        this.mediaUrl = mediaUrl;
        this.width = width;
        this.height = height;
        this.sizeBytes = sizeBytes;
    }
}

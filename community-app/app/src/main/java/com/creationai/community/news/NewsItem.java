package com.creationai.community.news;

import org.json.JSONException;
import org.json.JSONObject;

public final class NewsItem {
    public final String title;
    public final String summary;
    public final String url;
    public final String source;
    public final long publishedAtEpochMs;
    public final int relevanceScore;

    public NewsItem(String title, String summary, String url, String source,
                    long publishedAtEpochMs, int relevanceScore) {
        this.title = title == null ? "" : title.trim();
        this.summary = summary == null ? "" : summary.trim();
        this.url = url == null ? "" : url.trim();
        this.source = source == null ? "" : source.trim();
        this.publishedAtEpochMs = publishedAtEpochMs;
        this.relevanceScore = relevanceScore;
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("title", title)
                .put("summary", summary)
                .put("url", url)
                .put("source", source)
                .put("published_at_ms", publishedAtEpochMs)
                .put("relevance_score", relevanceScore);
    }
}

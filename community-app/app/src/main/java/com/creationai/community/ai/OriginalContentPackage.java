package com.creationai.community.ai;

import com.creationai.community.news.NewsItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class OriginalContentPackage {
    public final String topic;
    public final String title;
    public final String article;
    public final String videoScript;
    public final String coverPrompt;
    public final List<String> shotList;
    public final List<String> hashtags;
    public final List<NewsItem> sources;
    public final long generatedAtEpochMs;

    public OriginalContentPackage(String topic, String title, String article,
                                  String videoScript, String coverPrompt,
                                  List<String> shotList, List<String> hashtags,
                                  List<NewsItem> sources, long generatedAtEpochMs) {
        this.topic = clean(topic);
        this.title = clean(title);
        this.article = clean(article);
        this.videoScript = clean(videoScript);
        this.coverPrompt = clean(coverPrompt);
        this.shotList = immutableStrings(shotList);
        this.hashtags = immutableStrings(hashtags);
        this.sources = sources == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(sources));
        this.generatedAtEpochMs = generatedAtEpochMs;
    }

    public JSONObject toJson() throws JSONException {
        JSONArray shots = new JSONArray();
        for (String shot : shotList) shots.put(shot);
        JSONArray tags = new JSONArray();
        for (String tag : hashtags) tags.put(tag);
        JSONArray sourceValues = new JSONArray();
        for (NewsItem source : sources) sourceValues.put(source.toJson());
        return new JSONObject()
                .put("schema_version", 1)
                .put("topic", topic)
                .put("title", title)
                .put("article", article)
                .put("video_script", videoScript)
                .put("cover_prompt", coverPrompt)
                .put("shot_list", shots)
                .put("hashtags", tags)
                .put("sources", sourceValues)
                .put("generated_at_ms", generatedAtEpochMs);
    }

    static OriginalContentPackage fromModelJson(String topic, JSONObject model,
                                                List<NewsItem> sources) throws JSONException {
        String title = required(model, "title");
        String article = required(model, "article");
        String videoScript = required(model, "video_script");
        String coverPrompt = model.optString("cover_prompt", "").trim();
        List<String> shots = strings(model.optJSONArray("shot_list"));
        List<String> hashtags = strings(model.optJSONArray("hashtags"));
        if (shots.isEmpty()) throw new JSONException("模型未返回分镜列表");
        return new OriginalContentPackage(topic, title, article, videoScript, coverPrompt,
                shots, hashtags, sources, System.currentTimeMillis());
    }

    private static String required(JSONObject object, String key) throws JSONException {
        String value = object.optString(key, "").trim();
        if (value.isEmpty()) throw new JSONException("模型缺少字段：" + key);
        return value;
    }

    private static List<String> strings(JSONArray array) {
        List<String> result = new ArrayList<>();
        if (array == null) return result;
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "").trim();
            if (!value.isEmpty()) result.add(value);
        }
        return result;
    }

    private static List<String> immutableStrings(List<String> values) {
        if (values == null) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) result.add(value.trim());
        }
        return Collections.unmodifiableList(result);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}

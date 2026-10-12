package com.creationai.community.settings;

import android.content.Context;
import android.content.SharedPreferences;

public final class SettingsRepository {
    private static final String FILE = "community_settings";
    private final SharedPreferences prefs;
    private final SecretStore secrets;

    public SettingsRepository(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        secrets = new SecretStore(prefs);
    }

    public AppSettings load() {
        AppSettings value = new AppSettings();
        value.contentMode = ContentMode.fromStored(
                prefs.getString("content_mode", value.contentMode.name()));
        value.mode = prefs.getString("mode", value.mode);
        value.section = prefs.getString("section", value.section);
        value.keywords = prefs.getString("keywords", value.keywords);
        value.requiredTitleKeywords = prefs.getString(
                "required_title_keywords", value.requiredTitleKeywords);
        value.excludedKeywords = prefs.getString("excluded_keywords", value.excludedKeywords);
        value.minViews = prefs.getInt("min_views", value.minViews);
        value.minLikes = prefs.getInt("min_likes", value.minLikes);
        value.minFollowers = prefs.getInt("min_followers", value.minFollowers);
        value.maxFollowers = prefs.getInt("max_followers", value.maxFollowers);
        value.maxAgeHours = prefs.getInt("max_age_hours", value.maxAgeHours);
        value.maxPages = prefs.getInt("max_pages", value.maxPages);
        value.targetCount = prefs.getInt("target_count", value.targetCount);
        value.requireReview = prefs.getBoolean("require_review", value.requireReview);
        value.rightsConfirmed = prefs.getBoolean("rights_confirmed", value.rightsConfirmed);
        value.siliconFlowKey = secrets.loadApiKey();
        value.modelEndpoint = prefs.getString("model_endpoint", value.modelEndpoint);
        value.siliconFlowModel = prefs.getString("silicon_flow_model", value.siliconFlowModel);
        value.trimStartFrames = prefs.getInt("trim_start_frames",
                prefs.contains("trim_start_seconds")
                        ? Math.max(1, prefs.getInt("trim_start_seconds", 1) * 30)
                        : value.trimStartFrames);
        value.trimEndFrames = prefs.getInt("trim_end_frames",
                prefs.contains("trim_end_seconds")
                        ? Math.max(1, prefs.getInt("trim_end_seconds", 1) * 30)
                        : value.trimEndFrames);
        value.originalTopic = prefs.getString("original_topic", value.originalTopic);
        value.originalTags = prefs.getString("original_tags", value.originalTags);
        value.newsFeedUrls = prefs.getString("news_feed_urls", value.newsFeedUrls);
        value.newsLookbackHours = prefs.getInt("news_lookback_hours", value.newsLookbackHours);
        value.newsMaxItems = prefs.getInt("news_max_items", value.newsMaxItems);
        return value;
    }

    public void save(AppSettings value) {
        prefs.edit()
                .putString("content_mode", value.contentMode.name())
                .putString("mode", value.mode)
                .putString("section", value.section)
                .putString("keywords", value.keywords)
                .putString("required_title_keywords", value.requiredTitleKeywords)
                .putString("excluded_keywords", value.excludedKeywords)
                .putInt("min_views", value.minViews)
                .putInt("min_likes", value.minLikes)
                .putInt("min_followers", value.minFollowers)
                .putInt("max_followers", value.maxFollowers)
                .putInt("max_age_hours", value.maxAgeHours)
                .putInt("max_pages", value.maxPages)
                .putInt("target_count", value.targetCount)
                .putBoolean("require_review", value.requireReview)
                .putBoolean("rights_confirmed", value.rightsConfirmed)
                .putString("model_endpoint", value.modelEndpoint.trim())
                .putString("silicon_flow_model", value.siliconFlowModel.trim())
                .putInt("trim_start_frames", value.trimStartFrames)
                .putInt("trim_end_frames", value.trimEndFrames)
                .putString("original_topic", value.originalTopic)
                .putString("original_tags", value.originalTags)
                .putString("news_feed_urls", value.newsFeedUrls)
                .putInt("news_lookback_hours", value.newsLookbackHours)
                .putInt("news_max_items", value.newsMaxItems)
                .apply();
        secrets.saveApiKey(value.siliconFlowKey);
    }
}

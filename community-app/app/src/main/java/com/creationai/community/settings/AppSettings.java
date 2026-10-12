package com.creationai.community.settings;

public final class AppSettings {
    public ContentMode contentMode = ContentMode.AUTHORIZED_REUSE;
    public String mode = "搜索";
    public String section = "军事";
    public String keywords = "";
    public String requiredTitleKeywords = "";
    public String excludedKeywords = "";
    public int minViews = 1000;
    public int minLikes = 0;
    public int minFollowers = 0;
    public int maxFollowers = 0;
    public int maxAgeHours = 72;
    public int maxPages = 20;
    public int targetCount = 1;
    public boolean requireReview = true;
    public boolean rightsConfirmed = false;
    public String siliconFlowKey = "";
    public String modelEndpoint = "https://api.siliconflow.cn/v1/chat/completions";
    public String siliconFlowModel = "Qwen/Qwen2.5-7B-Instruct";
    public int trimStartFrames = 2;
    public int trimEndFrames = 2;

    public String originalTopic = "中美AI发展竞争";
    public String originalTags = "人工智能,科技,国际";
    public String newsFeedUrls = "";
    public int newsLookbackHours = 48;
    public int newsMaxItems = 12;
}

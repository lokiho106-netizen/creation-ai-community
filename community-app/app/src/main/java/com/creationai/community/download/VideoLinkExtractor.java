package com.creationai.community.download;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VideoLinkExtractor {
    private static final Pattern URL = Pattern.compile(
            "https?://(?:m\\.)?(?:toutiao\\.com|toutiaoapi\\.com|ixigua\\.com)/[^\\s]+",
            Pattern.CASE_INSENSITIVE);

    public String firstSupportedUrl(String sharedText) {
        if (sharedText == null) return null;
        Matcher matcher = URL.matcher(sharedText);
        if (!matcher.find()) return null;
        return trimPunctuation(matcher.group());
    }

    private String trimPunctuation(String value) {
        int end = value.length();
        while (end > 0 && "，。；！!?)）]】\"'".indexOf(value.charAt(end - 1)) >= 0) end--;
        return value.substring(0, end);
    }
}

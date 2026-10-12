package com.creationai.community.automation;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;

import com.creationai.community.settings.AppSettings;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;

public final class ToutiaoAdapter {
    private static final String PACKAGE = "com.ss.android.article.news";
    private static final String ID_SEARCH_ENTRY = PACKAGE + ":id/kic";
    private static final String ID_CHANNELS = PACKAGE + ":id/c6l";
    private static final String ID_FEED = PACKAGE + ":id/ao";
    private static final String ID_CARD = PACKAGE + ":id/bos";
    private static final String ID_TITLE = PACKAGE + ":id/l23";
    private static final String ID_TIME = PACKAGE + ":id/o08";
    private static final String ID_LIKES = PACKAGE + ":id/epp";
    private static final String ID_SHARE_TARGETS = PACKAGE + ":id/onk";
    private static final String ID_HOME_PUBLISH = PACKAGE + ":id/i56";
    private static final String ID_PREVIEW_NEXT =
            "com.bytedance.ugc.medialib.tt:id/video_preview_next_btn_v2";
    private static final String ID_FINAL_TITLE =
            "com.bytedance.ugc.medialib.tt:id/title_block_text";
    private static final String ID_FINAL_PUBLISH =
            "com.bytedance.ugc.medialib.tt:id/video_edit_publish";

    private final NodeFinder finder = new NodeFinder();

    public static final class Candidate {
        public final AccessibilityNodeInfo card;
        public final String title;
        public final int ageHours;
        public final long likes;
        public final long views;

        Candidate(AccessibilityNodeInfo card, String title, int ageHours, long likes, long views) {
            this.card = card;
            this.title = title;
            this.ageHours = ageHours;
            this.likes = likes;
            this.views = views;
        }
    }

    public boolean isHome(AccessibilityNodeInfo root) {
        return finder.byViewId(root, ID_FEED) != null && finder.byViewId(root, ID_SEARCH_ENTRY) != null;
    }

    public boolean dismissKnownBlockingOverlay(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo authorizationTitle = finder.byText(root, "授权后 体验完整功能");
        if (authorizationTitle == null) return false;
        AccessibilityNodeInfo close = finder.byDescription(root, "关闭");
        return click(close);
    }

    public boolean openSearch(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo entry = finder.byViewId(root, ID_SEARCH_ENTRY);
        return click(entry);
    }

    public boolean selectChannel(AccessibilityNodeInfo root, String channel) {
        AccessibilityNodeInfo node = exactDescription(root, channel);
        return click(node);
    }

    public boolean isChannelSelected(AccessibilityNodeInfo root, String channel) {
        AccessibilityNodeInfo node = exactDescription(root, channel);
        return node != null && node.isSelected();
    }

    public boolean isChannelVisible(AccessibilityNodeInfo root, String channel) {
        return exactDescription(root, channel) != null;
    }

    public boolean scrollChannelsForward(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo channels = finder.byViewId(root, ID_CHANNELS);
        return channels != null && channels.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
    }

    public boolean enterKeywordAndSearch(AccessibilityNodeInfo root, String keyword) {
        AccessibilityNodeInfo editor = firstEditable(root);
        if (editor == null) return false;
        Bundle arguments = new Bundle();
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, keyword);
        if (!editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)) return false;

        AccessibilityNodeInfo action = clickableAction(root, "搜索", editor);
        return action != null && action.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    public Candidate firstMatchingCandidate(AccessibilityNodeInfo root, AppSettings settings,
                                            Set<String> visitedTitles) {
        List<AccessibilityNodeInfo> cards = root.findAccessibilityNodeInfosByViewId(ID_CARD);
        if (cards == null) return null;
        for (AccessibilityNodeInfo card : cards) {
            if (!card.isVisibleToUser() || !card.isClickable()) continue;
            Rect bounds = new Rect();
            card.getBoundsInScreen(bounds);
            if (bounds.height() < 260) continue;

            String title = textOf(finder.byViewId(card, ID_TITLE));
            String timeText = textOf(finder.byViewId(card, ID_TIME));
            String likesText = textOf(finder.byViewId(card, ID_LIKES));
            int ageHours = parseAgeHours(timeText);
            long likes = parseCompactCount(likesText);
            long views = findLabeledCount(card, "播放");
            if (title.isEmpty() || ageHours < 0 || likes < 0) continue;
            if (visitedTitles != null && visitedTitles.contains(title)) continue;
            if (ageHours > settings.maxAgeHours || likes < settings.minLikes) continue;
            if (settings.minViews > 0 && (views < 0 || views < settings.minViews)) continue;
            if (!containsAnyRequired(title, settings.requiredTitleKeywords)) continue;
            if (containsExcluded(title, settings.excludedKeywords)) continue;
            return new Candidate(card, title, ageHours, likes, views);
        }
        return null;
    }

    public boolean scrollFeedForward(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo recycler = firstScrollableClass(root, "androidx.recyclerview.widget.RecyclerView");
        if (recycler == null) recycler = finder.byViewId(root, ID_FEED);
        return recycler != null && recycler.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
    }

    public boolean clickCandidate(Candidate candidate) {
        return candidate != null && click(candidate.card);
    }

    public boolean isVideoDetail(String eventClassName, AccessibilityNodeInfo root) {
        if (eventClassName != null && eventClassName.contains("TikTokActivity")) return true;
        return clickableAction(root, "分享", null) != null;
    }

    public boolean openSharePanel(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo share = clickableAction(root, "分享", null);
        return share != null && share.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    public boolean copyLink(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo copy = clickableAction(root, "复制链接", null);
        return copy != null && copy.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    public boolean scrollShareTargetsForward(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo targets = finder.byViewId(root, ID_SHARE_TARGETS);
        return targets != null && targets.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
    }

    public boolean openPublishEntry(AccessibilityNodeInfo root) {
        return click(finder.byViewId(root, ID_HOME_PUBLISH));
    }

    public boolean isPublishChooser(String eventClassName, AccessibilityNodeInfo root) {
        return eventClassName != null && eventClassName.contains("AggrPublishActivity")
                && finder.byText(root, "全部视频") != null;
    }

    public boolean selectNewestVideo(AccessibilityNodeInfo root) {
        return click(descriptionStartingWith(root, "视频0"));
    }

    public boolean isVideoPreview(String eventClassName, AccessibilityNodeInfo root) {
        return eventClassName != null && eventClassName.contains("VideoPreviewActivity")
                && finder.byViewId(root, ID_PREVIEW_NEXT) != null;
    }

    public boolean continueFromVideoPreview(AccessibilityNodeInfo root) {
        return click(finder.byViewId(root, ID_PREVIEW_NEXT));
    }

    public boolean isFinalPublishReview(String eventClassName, AccessibilityNodeInfo root) {
        return eventClassName != null && eventClassName.contains("VideoEditPublishActivity")
                && finder.byViewId(root, ID_FINAL_PUBLISH) != null
                && finder.byViewId(root, ID_FINAL_TITLE) != null;
    }

    public boolean fillFinalTitle(AccessibilityNodeInfo root, String title) {
        AccessibilityNodeInfo editor = finder.byViewId(root, ID_FINAL_TITLE);
        if (editor == null || title == null || title.trim().isEmpty()) return false;
        Bundle arguments = new Bundle();
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                title.trim());
        return editor.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
    }

    private boolean click(AccessibilityNodeInfo node) {
        if (node == null) return false;
        AccessibilityNodeInfo action = node.isClickable() ? node : finder.clickableAncestor(node);
        return action != null && action.performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private AccessibilityNodeInfo firstEditable(AccessibilityNodeInfo root) {
        if (root == null) return null;
        Queue<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.remove();
            if (node.isVisibleToUser() && node.isEditable() && node.isEnabled()) return node;
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        return null;
    }

    private AccessibilityNodeInfo clickableAction(AccessibilityNodeInfo root, String value,
                                                   AccessibilityNodeInfo excluded) {
        if (root == null) return null;
        Queue<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.remove();
            CharSequence text = node.getText();
            CharSequence description = node.getContentDescription();
            boolean matches = (text != null && value.contentEquals(text))
                    || (description != null && description.toString().contains(value));
            if (matches && node != excluded) {
                AccessibilityNodeInfo clickable = node.isClickable() ? node : finder.clickableAncestor(node);
                if (clickable != null && clickable != excluded) return clickable;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        return null;
    }

    private AccessibilityNodeInfo exactDescription(AccessibilityNodeInfo root, String value) {
        if (root == null || value == null || value.isEmpty()) return null;
        Queue<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.remove();
            CharSequence description = node.getContentDescription();
            if (node.isVisibleToUser() && description != null && value.contentEquals(description)) {
                return node;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        return null;
    }

    private AccessibilityNodeInfo descriptionStartingWith(AccessibilityNodeInfo root, String value) {
        if (root == null || value == null || value.isEmpty()) return null;
        Queue<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.remove();
            CharSequence description = node.getContentDescription();
            if (node.isVisibleToUser() && description != null
                    && description.toString().startsWith(value)) return node;
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        return null;
    }

    private AccessibilityNodeInfo firstScrollableClass(AccessibilityNodeInfo root, String className) {
        if (root == null) return null;
        Queue<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.remove();
            if (node.isVisibleToUser() && node.isScrollable()
                    && className.contentEquals(node.getClassName())) return node;
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        return null;
    }

    private String textOf(AccessibilityNodeInfo node) {
        return node == null || node.getText() == null ? "" : node.getText().toString().trim();
    }

    private long findLabeledCount(AccessibilityNodeInfo root, String label) {
        if (root == null) return -1;
        Queue<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            AccessibilityNodeInfo node = queue.remove();
            String text = node.getText() == null ? "" : node.getText().toString().trim();
            String description = node.getContentDescription() == null
                    ? "" : node.getContentDescription().toString().trim();
            if (text.contains(label)) {
                long count = parseCompactCount(text);
                if (count >= 0) return count;
            }
            if (description.contains(label)) {
                long count = parseCompactCount(description);
                if (count >= 0) return count;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) queue.add(child);
            }
        }
        return -1;
    }

    static boolean containsAnyRequired(String title, String requiredKeywords) {
        if (requiredKeywords == null || requiredKeywords.trim().isEmpty()) return true;
        if (title == null) return false;
        String normalizedTitle = title.toLowerCase(Locale.CHINA);
        for (String word : requiredKeywords.split("[,，;；\\n]")) {
            String normalizedWord = word.trim().toLowerCase(Locale.CHINA);
            if (!normalizedWord.isEmpty() && normalizedTitle.contains(normalizedWord)) return true;
        }
        return false;
    }

    private boolean containsExcluded(String title, String excludedKeywords) {
        if (excludedKeywords == null || excludedKeywords.trim().isEmpty()) return false;
        for (String word : excludedKeywords.split("[,，;；\\n]")) {
            if (!word.trim().isEmpty()
                    && title.toLowerCase(Locale.CHINA).contains(word.trim().toLowerCase(Locale.CHINA))) {
                return true;
            }
        }
        return false;
    }

    static int parseAgeHours(String value) {
        if (value == null || value.trim().isEmpty()) return -1;
        String text = value.trim();
        if (text.contains("刚刚") || text.contains("分钟")) return 0;
        if (text.contains("昨天")) return 24;
        try {
            if (text.contains("小时")) return Integer.parseInt(text.replaceAll("[^0-9]", ""));
            if (text.contains("天")) return Integer.parseInt(text.replaceAll("[^0-9]", "")) * 24;
        } catch (NumberFormatException ignored) {
        }
        return -1;
    }

    static long parseCompactCount(String value) {
        if (value == null || value.trim().isEmpty()) return -1;
        String text = value.trim().replace(",", "");
        double multiplier = 1;
        if (text.contains("万")) multiplier = 10_000;
        else if (text.contains("亿")) multiplier = 100_000_000;
        String number = text.replaceAll("[^0-9.]", "");
        if (number.isEmpty()) return -1;
        try {
            return Math.round(Double.parseDouble(number) * multiplier);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}

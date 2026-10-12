package com.creationai.community.news;

import android.text.Html;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class NewsFeedClient {
    private static final int MAX_FEEDS = 10;
    private static final int MAX_FEED_BYTES = 2 * 1024 * 1024;

    public List<NewsItem> fetch(String feedUrls, String topic, String tags,
                                int lookbackHours, int maxItems) throws IOException {
        List<String> urls = parseFeedUrls(feedUrls);
        if (urls.isEmpty()) throw new IOException("请至少填写一个 RSS/Atom 资讯源地址");
        if (urls.size() > MAX_FEEDS) throw new IOException("资讯源最多支持 " + MAX_FEEDS + " 个");

        List<String> terms = searchTerms(topic, tags);
        List<NewsItem> all = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (String value : urls) {
            try {
                URL url = validatedHttpsUrl(value);
                byte[] xml = download(url);
                all.addAll(parse(xml, url.getHost(), terms));
            } catch (Exception error) {
                failures.add(hostOrValue(value) + "：" + safeMessage(error));
            }
        }
        if (all.isEmpty()) {
            throw new IOException(failures.isEmpty()
                    ? "资讯源没有返回可用条目"
                    : "资讯源读取失败：" + join(failures));
        }

        long cutoff = lookbackHours <= 0 ? 0
                : System.currentTimeMillis() - lookbackHours * 3_600_000L;
        Set<String> seenUrls = new LinkedHashSet<>();
        List<NewsItem> filtered = new ArrayList<>();
        for (NewsItem item : all) {
            if (item.title.isEmpty() || item.url.isEmpty() || !seenUrls.add(item.url)) continue;
            if (cutoff > 0 && item.publishedAtEpochMs > 0 && item.publishedAtEpochMs < cutoff) continue;
            if (!terms.isEmpty() && item.relevanceScore <= 0) continue;
            filtered.add(item);
        }
        if (filtered.isEmpty()) {
            throw new IOException("指定时间范围内没有找到与主题或标签匹配的资讯");
        }
        Collections.sort(filtered, Comparator
                .comparingInt((NewsItem item) -> item.relevanceScore).reversed()
                .thenComparing(Comparator
                        .comparingLong((NewsItem item) -> item.publishedAtEpochMs)
                        .reversed()));
        int safeMax = Math.max(1, Math.min(30, maxItems));
        if (filtered.size() > safeMax) return new ArrayList<>(filtered.subList(0, safeMax));
        return filtered;
    }

    private List<NewsItem> parse(byte[] content, String source, List<String> terms)
            throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new ByteArrayInputStream(content), StandardCharsets.UTF_8.name());
        List<NewsItem> result = new ArrayList<>();
        String title = "";
        String summary = "";
        String link = "";
        String published = "";
        boolean inItem = false;
        int depth = -1;
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String name = localName(parser.getName());
                if ("item".equals(name) || "entry".equals(name)) {
                    inItem = true;
                    depth = parser.getDepth();
                    title = summary = link = published = "";
                    continue;
                }
                if (!inItem) continue;
                if ("title".equals(name)) title = parser.nextText();
                else if ("description".equals(name) || "summary".equals(name)
                        || "content".equals(name)) summary = parser.nextText();
                else if ("link".equals(name)) {
                    String href = parser.getAttributeValue(null, "href");
                    link = href == null || href.trim().isEmpty() ? parser.nextText() : href;
                } else if ("pubdate".equals(name) || "published".equals(name)
                        || "updated".equals(name) || "date".equals(name)) {
                    published = parser.nextText();
                }
            } else if (event == XmlPullParser.END_TAG && inItem
                    && parser.getDepth() == depth
                    && ("item".equals(localName(parser.getName()))
                    || "entry".equals(localName(parser.getName())))) {
                String cleanTitle = cleanHtml(title, 240);
                String cleanSummary = cleanHtml(summary, 800);
                result.add(new NewsItem(cleanTitle, cleanSummary, link, source,
                        parseDate(published), relevance(cleanTitle + " " + cleanSummary, terms)));
                inItem = false;
            }
        }
        return result;
    }

    private URL validatedHttpsUrl(String value) throws Exception {
        URI uri = new URI(value.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IOException("资讯源必须使用 HTTPS");
        }
        return uri.toURL();
    }

    private byte[] download(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(20_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml");
        connection.setRequestProperty("User-Agent", "CreationAI-Community/0.3 (+authorized-news-research)");
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            connection.disconnect();
            throw new IOException("HTTP " + status);
        }
        int declaredLength = connection.getContentLength();
        if (declaredLength > MAX_FEED_BYTES) {
            connection.disconnect();
            throw new IOException("响应超过 2 MB 上限");
        }
        try (InputStream input = connection.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_FEED_BYTES) throw new IOException("响应超过 2 MB 上限");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    private List<String> parseFeedUrls(String value) {
        List<String> result = new ArrayList<>();
        if (value == null) return result;
        for (String part : value.split("[\\n;；]+")) {
            String cleaned = part.trim();
            if (!cleaned.isEmpty()) result.add(cleaned);
        }
        return result;
    }

    private List<String> searchTerms(String topic, String tags) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        addTerms(result, topic);
        addTerms(result, tags);
        return new ArrayList<>(result);
    }

    private void addTerms(Set<String> target, String value) {
        if (value == null) return;
        String trimmed = value.trim().toLowerCase(Locale.CHINA);
        if (!trimmed.isEmpty()) target.add(trimmed);
        for (String part : value.split("[,，;；、\\s]+")) {
            String term = part.trim().toLowerCase(Locale.CHINA);
            if (term.length() >= 2) target.add(term);
        }
    }

    private int relevance(String text, List<String> terms) {
        String normalized = text.toLowerCase(Locale.CHINA);
        int score = 0;
        for (String term : terms) {
            if (normalized.contains(term)) score += Math.min(8, Math.max(1, term.length()));
        }
        return score;
    }

    private long parseDate(String value) {
        if (value == null || value.trim().isEmpty()) return 0;
        String text = value.trim();
        try {
            return ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant().toEpochMilli();
        } catch (RuntimeException ignored) {
        }
        try {
            return OffsetDateTime.parse(text).toInstant().toEpochMilli();
        } catch (RuntimeException ignored) {
        }
        try {
            return Instant.parse(text).toEpochMilli();
        } catch (RuntimeException ignored) {
        }
        String[] patterns = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd"};
        for (String pattern : patterns) {
            try {
                Date parsed = new SimpleDateFormat(pattern, Locale.CHINA).parse(text);
                if (parsed != null) return parsed.getTime();
            } catch (ParseException ignored) {
            }
        }
        return 0;
    }

    private String cleanHtml(String value, int maxLength) {
        if (value == null || value.trim().isEmpty()) return "";
        String plain = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
                .replaceAll("\\s+", " ").trim();
        return plain.length() > maxLength ? plain.substring(0, maxLength) : plain;
    }

    private String localName(String value) {
        if (value == null) return "";
        int colon = value.indexOf(':');
        return (colon >= 0 ? value.substring(colon + 1) : value).toLowerCase(Locale.US);
    }

    private String hostOrValue(String value) {
        try {
            String host = new URI(value).getHost();
            return host == null ? value : host;
        } catch (Exception ignored) {
            return value;
        }
    }

    private String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append("；");
            result.append(value);
        }
        return result.toString();
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName() : message;
    }
}

package com.creationai.community.download;

import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ToutiaoVideoResolver {
    private static final int CONNECT_TIMEOUT_MS = 12_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    private static final int MAX_PAGE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36";
    private static final Pattern RENDER_DATA = Pattern.compile(
            "<script\\s+id=\\\"RENDER_DATA\\\"\\s+type=\\\"application/json\\\">([^<]+)</script>",
            Pattern.CASE_INSENSITIVE);

    public ResolvedVideo resolve(String sharedUrl) throws IOException {
        UrlPolicy.requireSharePage(sharedUrl);
        String html = fetchTextFollowingPageRedirects(sharedUrl);
        JSONObject renderData = parseRenderData(html);
        JSONObject article = renderData.optJSONObject("articleInfo");
        if (article == null) throw new IOException("页面没有 articleInfo，可能已失效或不是视频");

        String sourceId = article.optString("gid", "");
        String title = article.optString("title", "今日头条视频");
        String playToken = article.optString("playAuthTokenV2", "");
        if (playToken.isEmpty()) throw new IOException("页面没有官方播放令牌");

        String playInfoQuery = decodePlayInfoQuery(playToken);
        String playInfoUrl = "https://vod.bytedanceapi.com/?" + playInfoQuery;
        UrlPolicy.requirePlayInfo(playInfoUrl);
        JSONObject playInfo;
        try {
            playInfo = new JSONObject(fetchText(playInfoUrl, MAX_PAGE_BYTES, false));
        } catch (JSONException e) {
            throw new IOException("官方播放信息格式无法解析", e);
        }
        JSONArray list = playInfo.optJSONObject("Result") == null
                ? null
                : playInfo.optJSONObject("Result").optJSONObject("Data") == null
                ? null
                : playInfo.optJSONObject("Result").optJSONObject("Data").optJSONArray("PlayInfoList");
        if (list == null || list.length() == 0) throw new IOException("官方接口未返回可播放的视频");

        JSONObject selected = chooseMobileQuality(list);
        String mediaUrl = selected.optString("MainPlayUrl", "");
        if (mediaUrl.isEmpty()) mediaUrl = selected.optString("BackupPlayUrl", "");
        UrlPolicy.requireMedia(mediaUrl);
        return new ResolvedVideo(
                sourceId,
                title,
                mediaUrl,
                selected.optInt("Width", 0),
                selected.optInt("Height", 0),
                selected.optLong("Size", -1));
    }

    private JSONObject parseRenderData(String html) throws IOException {
        Matcher matcher = RENDER_DATA.matcher(html);
        if (!matcher.find()) throw new IOException("页面未包含 RENDER_DATA");
        try {
            String decoded = Uri.decode(matcher.group(1));
            return new JSONObject(decoded);
        } catch (JSONException e) {
            throw new IOException("页面数据格式无法解析", e);
        }
    }

    private String decodePlayInfoQuery(String token) throws IOException {
        try {
            byte[] decoded = Base64.decode(token, Base64.DEFAULT);
            JSONObject envelope = new JSONObject(new String(decoded, StandardCharsets.UTF_8));
            String query = envelope.optString("GetPlayInfoToken", "");
            if (query.isEmpty()) throw new IOException("播放令牌内容为空");
            return query;
        } catch (IllegalArgumentException | JSONException e) {
            throw new IOException("播放令牌无法解码", e);
        }
    }

    private JSONObject chooseMobileQuality(JSONArray list) throws IOException {
        JSONObject best = null;
        int bestHeight = -1;
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null || !"mp4".equalsIgnoreCase(item.optString("Format"))) continue;
            int height = item.optInt("Height", 0);
            if (height <= 720 && height > bestHeight) {
                best = item;
                bestHeight = height;
            }
        }
        if (best == null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item != null && !item.optString("MainPlayUrl", "").isEmpty()) return item;
            }
            throw new IOException("没有兼容的 MP4 清晰度");
        }
        return best;
    }

    private String fetchTextFollowingPageRedirects(String initialUrl) throws IOException {
        String current = initialUrl;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            UrlPolicy.requireSharePage(current);
            HttpURLConnection connection = open(current, false);
            int code = connection.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null) throw new IOException("分享链接跳转缺少目标地址");
                current = UrlPolicy.resolveRedirect(current, location);
                continue;
            }
            if (code < 200 || code >= 300) {
                connection.disconnect();
                throw new IOException("页面请求失败：HTTP " + code);
            }
            try (InputStream input = connection.getInputStream()) {
                return readUtf8(input, MAX_PAGE_BYTES);
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("分享链接跳转次数过多");
    }

    private String fetchText(String url, int maxBytes, boolean pageHost) throws IOException {
        if (pageHost) UrlPolicy.requireSharePage(url);
        HttpURLConnection connection = open(url, true);
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new IOException("接口请求失败：HTTP " + code);
        }
        try (InputStream input = connection.getInputStream()) {
            return readUtf8(input, maxBytes);
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection open(String url, boolean followRedirects) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(followRedirects);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/json,text/html;q=0.9,*/*;q=0.8");
        return connection;
    }

    private String readUtf8(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) throw new IOException("响应内容超过安全上限");
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }
}

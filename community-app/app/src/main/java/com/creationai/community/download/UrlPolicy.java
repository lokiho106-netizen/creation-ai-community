package com.creationai.community.download;

import android.net.Uri;

import java.io.IOException;
import java.util.Locale;

final class UrlPolicy {
    private UrlPolicy() {
    }

    static void requireSharePage(String rawUrl) throws IOException {
        String host = requireHttpsHost(rawUrl);
        if (!isHostOrSubdomain(host, "toutiao.com")
                && !isHostOrSubdomain(host, "toutiaoapi.com")
                && !isHostOrSubdomain(host, "ixigua.com")) {
            throw new IOException("不支持的分享链接域名：" + host);
        }
    }

    static void requirePlayInfo(String rawUrl) throws IOException {
        String host = requireHttpsHost(rawUrl);
        if (!"vod.bytedanceapi.com".equals(host)) {
            throw new IOException("拒绝访问非官方播放信息域名：" + host);
        }
    }

    static void requireMedia(String rawUrl) throws IOException {
        String host = requireHttpsHost(rawUrl);
        if (!isHostOrSubdomain(host, "toutiaovod.com")
                && !isHostOrSubdomain(host, "bytevcloud.com")
                && !isHostOrSubdomain(host, "volcvod.com")
                && !isHostOrSubdomain(host, "bytecdn.cn")) {
            throw new IOException("拒绝下载非官方媒体域名：" + host);
        }
    }

    static String resolveRedirect(String base, String location) throws IOException {
        Uri resolved = Uri.parse(base).buildUpon().encodedPath(Uri.parse(base).getEncodedPath()).build();
        try {
            return new java.net.URL(new java.net.URL(resolved.toString()), location).toString();
        } catch (Exception e) {
            throw new IOException("无效的跳转地址", e);
        }
    }

    private static String requireHttpsHost(String rawUrl) throws IOException {
        Uri uri = Uri.parse(rawUrl);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(scheme) || host == null || host.isEmpty()) {
            throw new IOException("只允许 HTTPS 链接");
        }
        return host.toLowerCase(Locale.ROOT);
    }

    private static boolean isHostOrSubdomain(String host, String root) {
        return host.equals(root) || host.endsWith("." + root);
    }
}

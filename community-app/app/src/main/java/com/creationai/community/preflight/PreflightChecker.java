package com.creationai.community.preflight;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.view.accessibility.AccessibilityManager;

import java.util.List;

public final class PreflightChecker {
    public static final String TOUTIAO_PACKAGE = "com.ss.android.article.news";
    public static final String EXPECTED_TOUTIAO_VERSION = "15.4.0";

    private final Context context;

    public PreflightChecker(Context context) {
        this.context = context;
    }

    public PreflightReport run() {
        boolean installed = false;
        String version = "未安装";
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(TOUTIAO_PACKAGE, 0);
            installed = true;
            version = info.versionName == null ? "未知版本" : info.versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        AccessibilityManager manager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> enabled = manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        boolean accessibilityEnabled = false;
        for (AccessibilityServiceInfo info : enabled) {
            android.content.pm.ServiceInfo serviceInfo = info.getResolveInfo() == null
                    ? null : info.getResolveInfo().serviceInfo;
            if (serviceInfo != null
                    && context.getPackageName().equals(serviceInfo.packageName)
                    && "com.creationai.community.automation.CreationAccessibilityService".equals(serviceInfo.name)) {
                accessibilityEnabled = true;
                break;
            }
        }

        ConnectivityManager connectivity =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network active = connectivity.getActiveNetwork();
        NetworkCapabilities caps = active == null ? null : connectivity.getNetworkCapabilities(active);
        boolean online = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        return new PreflightReport(installed, version, EXPECTED_TOUTIAO_VERSION.equals(version),
                accessibilityEnabled, online);
    }
}

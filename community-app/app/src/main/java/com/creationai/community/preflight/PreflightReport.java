package com.creationai.community.preflight;

public final class PreflightReport {
    public final boolean toutiaoInstalled;
    public final String toutiaoVersion;
    public final boolean toutiaoVersionCompatible;
    public final boolean accessibilityEnabled;
    public final boolean networkAvailable;

    public PreflightReport(boolean toutiaoInstalled, String toutiaoVersion,
                           boolean toutiaoVersionCompatible,
                           boolean accessibilityEnabled, boolean networkAvailable) {
        this.toutiaoInstalled = toutiaoInstalled;
        this.toutiaoVersion = toutiaoVersion;
        this.toutiaoVersionCompatible = toutiaoVersionCompatible;
        this.accessibilityEnabled = accessibilityEnabled;
        this.networkAvailable = networkAvailable;
    }

    public boolean canStart() {
        return toutiaoInstalled && toutiaoVersionCompatible && accessibilityEnabled && networkAvailable;
    }
}

package com.haricalcx.haribrowser;

/**
 * Small, deterministic policy for Lite/Data Saver mode.
 *
 * <p>Data Saver only changes whether remote webpage images load. It does not
 * claim to count bytes saved, block scripts/video, or make a page offline.</p>
 */
final class DataSaverPolicy {
    private DataSaverPolicy() {}

    static boolean shouldBlockNetworkImages(
            boolean homePage, boolean userAllowsImages, boolean dataSaverEnabled) {
        return !homePage && (!userAllowsImages || dataSaverEnabled);
    }

    static String description(boolean dataSaverEnabled) {
        return dataSaverEnabled
                ? "ON — webpage images are blocked to reduce data use. Scripts, video, and other requests may still use data."
                : "OFF — website images follow the separate image setting.";
    }
}

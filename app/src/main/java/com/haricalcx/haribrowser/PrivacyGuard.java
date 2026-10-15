package com.haricalcx.haribrowser;

import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Local, network-free content blocking. This is a useful baseline, not a complete filter list. */
public final class PrivacyGuard {
    private PrivacyGuard() {}

    private static final Set<String> TRACKER_HOSTS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "adservice.google.com",
            "googletagmanager.com",
            "google-analytics.com",
            "analytics.google.com",
            "connect.facebook.net",
            "pixel.facebook.com",
            "amazon-adsystem.com",
            "hotjar.com",
            "segment.io",
            "segment.com",
            "mixpanel.com",
            "clarity.ms",
            "bat.bing.com",
            "scorecardresearch.com",
            "taboola.com",
            "outbrain.com",
            "adnxs.com",
            "criteo.com",
            "quantserve.com",
            "branch.io",
            "app-measurement.com",
            "adjust.com",
            "appsflyer.com",
            "ads-twitter.com",
            "analytics.twitter.com",
            "snap.licdn.com",
            "ads.linkedin.com"
    )));

    public static boolean isHttpUrl(String value) {
        try {
            return "http".equalsIgnoreCase(Uri.parse(value).getScheme());
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isHttpsUrl(String value) {
        try {
            Uri uri = Uri.parse(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isAllowedWebUrl(String value) {
        return isHttpsUrl(value);
    }

    public static WebResourceResponse intercept(WebResourceRequest request, boolean blockingEnabled) {
        Uri uri = request.getUrl();
        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme)) {
            return emptyBlockedResponse();
        }
        if (blockingEnabled && "https".equalsIgnoreCase(scheme)) {
            String host = uri.getHost();
            if (host != null && isTrackerHost(host)) {
                return emptyBlockedResponse();
            }
        }
        return null;
    }

    public static boolean isTrackerHost(String candidate) {
        if (candidate == null) return false;
        String host = candidate.toLowerCase(Locale.ROOT);
        for (String tracker : TRACKER_HOSTS) {
            if (host.equals(tracker) || host.endsWith("." + tracker)) return true;
        }
        return false;
    }

    private static WebResourceResponse emptyBlockedResponse() {
        return new WebResourceResponse(
                "text/plain",
                "UTF-8",
                204,
                "Blocked by Hari Browser",
                Collections.emptyMap(),
                new ByteArrayInputStream(new byte[0]));
    }
}

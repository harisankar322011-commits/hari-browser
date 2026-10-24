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
            "ads.linkedin.com",
            // India-relevant mobile/native/video advertising and attribution networks.
            "inmobi.com",
            "adgebra.co",
            "vserv.com",
            "silverpush.co",
            "pokkt.com"
    )));

    private static volatile Set<String> CUSTOM_TRACKER_HOSTS = Collections.emptySet();

    /** Load a user-imported, hosts-format list. Invalid stored data fails closed to the built-in list. */
    public static void setCustomTrackerList(String text) {
        try {
            CUSTOM_TRACKER_HOSTS = TrackerFilterList.parseHosts(text);
        } catch (IllegalArgumentException invalidStoredList) {
            CUSTOM_TRACKER_HOSTS = Collections.emptySet();
        }
    }

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

    /**
     * Returns the same URL with only its scheme upgraded from HTTP to HTTPS.
     * This does not open HTTP and does not establish that the HTTPS destination
     * exists or is trustworthy; callers must still run the normal site preflight.
     */
    public static String httpsUpgradeUrl(String value) {
        if (value == null) return null;
        String candidate = value.trim();
        try {
            java.net.URI uri = new java.net.URI(candidate);
            if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                return null;
            }
            return "https" + candidate.substring(uri.getScheme().length());
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean isAllowedWebUrl(String value) {
        return isHttpsUrl(value);
    }

    /**
     * Keeps bundled home-page images available while honoring normal image settings
     * and Lite mode for web pages. Lite mode blocks network images only; it is not
     * a compression proxy and does not measure data saved.
     */
    public static boolean shouldBlockNetworkImages(
            boolean homePage, boolean imagesEnabled, boolean liteModeEnabled) {
        return !homePage && (!imagesEnabled || liteModeEnabled);
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
        if (matchesTrackerSet(host, TRACKER_HOSTS)) return true;
        return matchesTrackerSet(host, CUSTOM_TRACKER_HOSTS);
    }

    private static boolean matchesTrackerSet(String host, Set<String> trackers) {
        // Walk only the candidate's DNS suffixes so large imported lists remain fast.
        if (trackers.contains(host)) return true;
        int dot = host.indexOf('.');
        while (dot >= 0 && dot < host.length() - 1) {
            if (trackers.contains(host.substring(dot + 1))) return true;
            dot = host.indexOf('.', dot + 1);
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

package com.haricalcx.haribrowser;

import org.junit.Test;

import static org.junit.Assert.*;

public class PrivacyGuardTest {
    @Test
    public void blocksSelectedIndiaRelevantAdvertisingNetworks() {
        assertTrue(PrivacyGuard.isTrackerHost("ads.inmobi.com"));
        assertTrue(PrivacyGuard.isTrackerHost("cdn.adgebra.co"));
        assertTrue(PrivacyGuard.isTrackerHost("vmax.vserv.com"));
        assertTrue(PrivacyGuard.isTrackerHost("player.silverpush.co"));
        assertTrue(PrivacyGuard.isTrackerHost("ads.pokkt.com"));
    }

    @Test
    public void upgradesHttpSchemeWithoutChangingPathQueryOrFragment() {
        assertEquals("https://municipality.example:8080/services?q=water%20bill#status",
                PrivacyGuard.httpsUpgradeUrl(
                        "http://municipality.example:8080/services?q=water%20bill#status"));
        assertEquals("https://example.in/path",
                PrivacyGuard.httpsUpgradeUrl("HTTP://example.in/path"));
    }

    @Test
    public void refusesToUpgradeNonHttpOrMalformedAddresses() {
        assertNull(PrivacyGuard.httpsUpgradeUrl("https://example.in/"));
        assertNull(PrivacyGuard.httpsUpgradeUrl("javascript:alert(1)"));
        assertNull(PrivacyGuard.httpsUpgradeUrl("http:///missing-host"));
        assertNull(PrivacyGuard.httpsUpgradeUrl(null));
    }


    @Test
    public void liteModeBlocksImagesOnWebPagesButNotTheHomeDashboard() {
        assertFalse(PrivacyGuard.shouldBlockNetworkImages(true, true, true));
        assertFalse(PrivacyGuard.shouldBlockNetworkImages(false, true, false));
        assertTrue(PrivacyGuard.shouldBlockNetworkImages(false, true, true));
        assertTrue(PrivacyGuard.shouldBlockNetworkImages(false, false, false));
        assertTrue(PrivacyGuard.shouldBlockNetworkImages(false, false, true));
    }

    @Test
    public void trackerMatchingUsesDnsSuffixBoundaries() {
        assertTrue(PrivacyGuard.isTrackerHost("ads.example.inmobi.com"));
        assertFalse(PrivacyGuard.isTrackerHost("notinmobi.com.example"));
        assertFalse(PrivacyGuard.isTrackerHost("inmobi.com.example"));
        assertFalse(PrivacyGuard.isTrackerHost("example.com"));
        assertFalse(PrivacyGuard.isTrackerHost(null));
    }
    @Test
    public void customImportedHostsAreMatchedOnlyOnExactDnsSuffixBoundaries() {
        PrivacyGuard.setCustomTrackerList("0.0.0.0 ads.custom.example\ntracker.custom.example # optional note\n");
        assertTrue(PrivacyGuard.isTrackerHost("ads.custom.example"));
        assertTrue(PrivacyGuard.isTrackerHost("cdn.ads.custom.example"));
        assertTrue(PrivacyGuard.isTrackerHost("tracker.custom.example"));
        assertFalse(PrivacyGuard.isTrackerHost("notcustom.example"));
        assertFalse(PrivacyGuard.isTrackerHost("custom.example.attacker"));
    }

    @Test
    public void invalidOrOversizedCustomListCannotReplaceBuiltInProtections() {
        PrivacyGuard.setCustomTrackerList("https://not-a-hosts-list");
        assertTrue(PrivacyGuard.isTrackerHost("ads.inmobi.com"));
        assertFalse(PrivacyGuard.isTrackerHost("example.com"));
    }

}

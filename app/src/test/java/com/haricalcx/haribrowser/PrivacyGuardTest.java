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
    public void trackerMatchingUsesDnsSuffixBoundaries() {
        assertTrue(PrivacyGuard.isTrackerHost("ads.example.inmobi.com"));
        assertFalse(PrivacyGuard.isTrackerHost("notinmobi.com.example"));
        assertFalse(PrivacyGuard.isTrackerHost("inmobi.com.example"));
        assertFalse(PrivacyGuard.isTrackerHost("example.com"));
        assertFalse(PrivacyGuard.isTrackerHost(null));
    }
}

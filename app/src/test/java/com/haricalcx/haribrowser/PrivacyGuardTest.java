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
    public void trackerMatchingUsesDnsSuffixBoundaries() {
        assertTrue(PrivacyGuard.isTrackerHost("ads.example.inmobi.com"));
        assertFalse(PrivacyGuard.isTrackerHost("notinmobi.com.example"));
        assertFalse(PrivacyGuard.isTrackerHost("inmobi.com.example"));
        assertFalse(PrivacyGuard.isTrackerHost("example.com"));
        assertFalse(PrivacyGuard.isTrackerHost(null));
    }
}

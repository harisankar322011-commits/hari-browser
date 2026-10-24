package com.haricalcx.haribrowser;

import org.junit.Test;

import static org.junit.Assert.*;

public final class FingerprintProtectionTest {
    @Test
    public void reducesAndroidBuildAndDetailedChromiumVersionFromUserAgent() {
        String source = "Mozilla/5.0 (Linux; Android 15; SM-X910 Build/AP3A.241005.015) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 "
                + "Chrome/143.0.7499.193 Mobile Safari/537.36";

        String reduced = FingerprintProtection.reduceUserAgent(source);

        assertTrue(reduced.contains("Android 10; K"));
        assertFalse(reduced.contains("Android 15"));
        assertFalse(reduced.contains("SM-X910"));
        assertFalse(reduced.contains("AP3A.241005.015"));
        assertTrue(reduced.contains("Chrome/143.0.0.0"));
        assertFalse(reduced.contains("143.0.7499.193"));
    }

    @Test
    public void preservesMajorEngineVersionWhenReducingAlreadyGenericUserAgent() {
        String source = "Mozilla/5.0 (Linux; Android 10; K; wv) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 "
                + "Chrome/142.0.7444.171 Mobile Safari/537.36";

        String reduced = FingerprintProtection.reduceUserAgent(source);

        assertTrue(reduced.contains("Android 10; K; wv"));
        assertTrue(reduced.contains("Chrome/142.0.0.0"));
    }

    @Test
    public void safelyHandlesMissingUserAgent() {
        assertEquals("", FingerprintProtection.reduceUserAgent(null));
        assertEquals("", FingerprintProtection.reduceUserAgent(""));
    }

    @Test
    public void scriptReducesHighEntropyNavigatorAndScreenSignals() {
        String script = FingerprintProtection.SCRIPT;

        assertTrue(script.contains("userAgentData"));
        assertTrue(script.contains("defineFixed(navigator, 'platform'"));
        assertTrue(script.contains("defineFixed(navigator, 'maxTouchPoints'"));
        assertTrue(script.contains("defineFixed(screen, 'availLeft'"));
        assertTrue(script.contains("defineFixed(screen, 'availTop'"));
        assertTrue(script.contains("devicePixelRatio"));
    }

    @Test
    public void scriptReducesHighResolutionTimingAndHeapInformation() {
        String script = FingerprintProtection.SCRIPT;

        assertTrue(script.contains("perf, 'memory'"));
        assertTrue(script.contains("Math.floor(originalNow.apply(this, arguments))"));
    }

    @Test
    public void scriptRetainsCanvasWebGlAndOfflineAudioReadbackProtections() {
        String script = FingerprintProtection.SCRIPT;

        assertTrue(script.contains("HTMLCanvasElement"));
        assertTrue(script.contains("webgl_debug_renderer_info"));
        assertTrue(script.contains("OfflineAudioContext"));
    }
}

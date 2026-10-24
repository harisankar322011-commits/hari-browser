package com.haricalcx.haribrowser;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/** Regression tests for the dedicated integrated protection dashboard entry point. */
public final class HomePageTest {
    @Test
    public void homePageOffersDedicatedSingularityProtectionDashboard() {
        String html = HomePage.render(false, "DuckDuckGo", "[]", false, "Ocean", "en");

        assertTrue(html.contains("href=\"https://hari-browser-home.invalid/action/protections\""));
        assertTrue(html.contains("HARI SINGULARITY Protection Dashboard"));
    }

    @Test
    public void siteSecurityDetailsRemainAvailableSeparately() {
        String html = HomePage.render(false, "DuckDuckGo", "[]", false, "Ocean", "en");

        assertTrue(html.contains("href=\"https://hari-browser-home.invalid/action/security\""));
    }
}

package com.haricalcx.haribrowser;

import org.junit.Test;

import static org.junit.Assert.*;

public class SiteSafetyCheckerTest {
    @Test
    public void ordinaryHttpsAddressHasNoUrlStructureWarnings() {
        SiteSafetyChecker.Report report = SiteSafetyChecker.inspect("https://example.com/path");
        assertEquals("example.com", report.getHost());
        assertFalse(report.hasWarnings());
        assertTrue(report.getPassedChecks().contains("HTTPS address requested"));
    }

    @Test
    public void curatedOfficialDomainMatchIsVisibleButIsNotASafetyVerdict() {
        SiteSafetyChecker.Report report = SiteSafetyChecker.inspect("https://www.sbicard.com/");
        String passed = String.join(" ", report.getPassedChecks());
        assertTrue(passed.contains("curated official-domain list: SBI Card"));
        assertTrue(passed.contains("not a guarantee"));
        assertFalse(String.join(" ", report.getWarnings()).contains("recognized official domain"));
    }

    @Test
    public void officialDomainIndicatorDoesNotAcceptHostnameSuffixLookalikes() {
        SiteSafetyChecker.Report report = SiteSafetyChecker.inspect("https://uidai.gov.in.example/");
        assertFalse(String.join(" ", report.getPassedChecks()).contains("curated official-domain list"));
        assertTrue(String.join(" ", report.getWarnings()).contains("recognized official domain"));
    }

    @Test
    public void nonHttpsAddressRaisesWarning() {
        SiteSafetyChecker.Report report = SiteSafetyChecker.inspect("http://example.com");
        assertTrue(report.hasWarnings());
        assertTrue(report.getWarnings().get(0).contains("not HTTPS"));
    }

    @Test
    public void embeddedCredentialsRaiseWarningWithoutEchoingSecrets() {
        SiteSafetyChecker.Report report = SiteSafetyChecker.inspect("https://alice:very-secret@example.com/");
        assertTrue(report.hasWarnings());
        String combined = String.join(" ", report.getWarnings());
        assertTrue(combined.contains("disguise the real destination"));
        assertFalse(combined.contains("very-secret"));
    }

    @Test
    public void punycodeHostnameRaisesCaution() {
        SiteSafetyChecker.Report report = SiteSafetyChecker.inspect("https://xn--pple-43d.com/");
        assertTrue(report.hasWarnings());
        assertTrue(String.join(" ", report.getWarnings()).contains("punycode"));
    }

    @Test
    public void ipLiteralAndNonStandardPortRaiseWarnings() {
        SiteSafetyChecker.Report report = SiteSafetyChecker.inspect("https://192.0.2.10:8443/");
        String combined = String.join(" ", report.getWarnings());
        assertTrue(combined.contains("IP address"));
        assertTrue(combined.contains("non-standard HTTPS port"));
    }

    @Test
    public void indianBankLookalikeAndOtpScamPatternsAreIncludedInSitePreflight() {
        SiteSafetyChecker.Report report = SiteSafetyChecker.inspect(
                "https://secure-sbi-kyc.example/verify?otp=1&urgent=refund");
        String combined = String.join(" ", report.getWarnings()).toLowerCase(java.util.Locale.ROOT);
        assertTrue(report.hasWarnings());
        assertTrue(combined.contains("recognized official domain"));
        assertTrue(combined.contains("possible indian payment"));
    }

    @Test
    public void invalidUrlRaisesWarningInsteadOfThrowing() {
        SiteSafetyChecker.Report report = SiteSafetyChecker.inspect("https://[not-an-ip");
        assertTrue(report.hasWarnings());
        assertEquals("unknown host", report.getHost());
    }

    @Test
    public void longQueryStringIsFlaggedAsPossibleSensitiveData() {
        StringBuilder url = new StringBuilder("https://example.com/?token=");
        for (int i = 0; i < 510; i++) url.append('a');
        SiteSafetyChecker.Report report = SiteSafetyChecker.inspect(url.toString());
        assertTrue(String.join(" ", report.getWarnings()).contains("long query string"));
    }

    @Test
    public void unicodeHostnameIsNormalizedToPunycodeAndFlagged() {
        String lookalikeHost = "\u0440\u0430ypal.example";
        SiteSafetyChecker.Report report = SiteSafetyChecker.inspect("https://" + lookalikeHost + "/login");

        assertEquals("xn--ypal-43d9g.example", report.getHost());
        String warnings = String.join(" ", report.getWarnings()).toLowerCase(java.util.Locale.ROOT);
        assertTrue(warnings.contains("internationalized domain name"));
        assertTrue(warnings.contains("punycode"));
    }

    @Test
    public void mixedLatinAndCyrillicHostnameGetsVisualSpoofingWarning() {
        String lookalikeHost = "\u0440\u0430ypal.example";
        SiteSafetyChecker.Report report = SiteSafetyChecker.inspect("https://" + lookalikeHost + "/login");
        String warnings = String.join(" ", report.getWarnings());

        assertTrue(warnings.contains("mixes Latin with Cyrillic or Greek"));
    }

    @Test
    public void unicodeHostnameDoesNotHideNonstandardPort() {
        String lookalikeHost = "\u0440\u0430ypal.example";
        SiteSafetyChecker.Report report = SiteSafetyChecker.inspect(
                "https://" + lookalikeHost + ":8443/login");

        assertTrue(String.join(" ", report.getWarnings()).contains("non-standard HTTPS port (8443)"));
    }
}

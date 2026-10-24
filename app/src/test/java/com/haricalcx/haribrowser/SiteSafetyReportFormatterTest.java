package com.haricalcx.haribrowser;

import org.junit.Test;

import static org.junit.Assert.*;

/** Security copy and decision-label regression tests for the site safety dialog. */
public final class SiteSafetyReportFormatterTest {
    @Test
    public void selectedLanguageLocalizesSafetyHeadingsTitleActionAndPrivacyDisclosure() {
        SiteSafetyReportFormatter.Presentation presentation =
                SiteSafetyReportFormatter.format(
                        "https://example.com/", SiteSafetyChecker.inspect("https://example.com/"),
                        null, true, false, "ml");

        assertEquals("സൈറ്റ് സുരക്ഷാ പരിശോധന", presentation.title);
        assertEquals("സൈറ്റ് തുറക്കുക", presentation.positiveLabel);
        assertTrue(presentation.message.contains("പ്രാദേശിക URL പരിശോധന"));
        assertTrue(presentation.message.contains("സൂക്ഷ്മ വിവരങ്ങൾക്കുള്ള ഓർമ്മപ്പെടുത്തൽ"));
        assertTrue(presentation.message.contains("ഈ പരിശോധന URL മൂന്നാം കക്ഷി സ്കാനറിലേക്ക് അയച്ചിട്ടില്ല."));
        assertFalse(presentation.message.contains("This check did not send the URL to a third-party scanner."));
    }

    @Test
    public void knownMalwareRecordGetsStrongWarningAndExplicitOverrideLabel() {
        UrlhausReputation.Result reputation = new UrlhausReputation.Result(
                true, "KNOWN_MALWARE_URL", "URLhaus reports a malware-distribution record.");
        SiteSafetyReportFormatter.Presentation presentation =
                SiteSafetyReportFormatter.format(
                        "https://malware.example/download", SiteSafetyChecker.inspect("https://malware.example/download"),
                        reputation, false, true);

        assertTrue(presentation.knownMalware);
        assertTrue(presentation.title.contains("Known malware-distribution URL"));
        assertEquals("I understand — open anyway", presentation.positiveLabel);
        assertTrue(presentation.message.contains("URLhaus malware-distribution record"));
        assertTrue(presentation.message.contains("This URL was sent to URLhaus"));
    }

    @Test
    public void urlhausNoMatchDoesNotClaimThatSiteIsSafe() {
        UrlhausReputation.Result reputation = new UrlhausReputation.Result(
                false, "NO_MATCH", "URLhaus returned no record.");
        SiteSafetyReportFormatter.Presentation presentation =
                SiteSafetyReportFormatter.format(
                        "https://example.com/", SiteSafetyChecker.inspect("https://example.com/"),
                        reputation, false, true);

        assertFalse(presentation.knownMalware);
        assertTrue(presentation.message.contains("No record does not mean safe"));
        assertTrue(presentation.message.contains("does not identify every phishing or scam site"));
    }

    @Test
    public void privateModeExplainsThatUrlWasNotSentToThirdPartyScanner() {
        SiteSafetyReportFormatter.Presentation presentation =
                SiteSafetyReportFormatter.format(
                        "https://example.com/", SiteSafetyChecker.inspect("https://example.com/"),
                        null, true, false);

        assertTrue(presentation.message.contains("This check did not send the URL to a third-party scanner."));
        assertFalse(presentation.message.contains("ONLINE URL REPUTATION (URLhaus)"));
        assertFalse(presentation.message.contains("This URL was sent to URLhaus"));
    }

    @Test
    public void authKeyMissingIsDisclosedWithoutCallingLocalChecksAFullVerdict() {
        SiteSafetyReportFormatter.Presentation presentation =
                SiteSafetyReportFormatter.format(
                        "https://example.com/", SiteSafetyChecker.inspect("https://example.com/"),
                        null, false, true);

        assertTrue(presentation.message.contains("no Auth-Key is configured"));
        assertTrue(presentation.message.contains("they do not certify the website"));
    }

    @Test
    public void sensitiveUrlReceivesReminderAndOrdinaryPageHasNormalOpenLabel() {
        SiteSafetyReportFormatter.Presentation presentation =
                SiteSafetyReportFormatter.format(
                        "https://example.com/login?otp=123",
                        SiteSafetyChecker.inspect("https://example.com/login?otp=123"),
                        null, false, false);

        assertTrue(presentation.message.contains("SENSITIVE INFORMATION REMINDER"));
        assertTrue(presentation.message.contains("Never share an OTP, bank PIN, password"));
        assertEquals("Site safety check", presentation.title);
        assertEquals("Open site", presentation.positiveLabel);
    }
}

package com.haricalcx.haribrowser;

import org.junit.Test;

import static org.junit.Assert.*;

public class SensitiveInfoReminderTest {
    @Test
    public void remindsForStrongAccountIdentityAndVerificationUrlSignals() {
        assertTrue(SensitiveInfoReminder.shouldRemind("https://uidai.gov.in/my-aadhaar/login"));
        assertTrue(SensitiveInfoReminder.shouldRemind("https://bank.example/netbanking/signin"));
        assertTrue(SensitiveInfoReminder.shouldRemind("https://example.com/otp/verify?flow=login"));
        assertTrue(SensitiveInfoReminder.shouldRemind("https://example.com/e-kyc/start"));
    }

    @Test
    public void doesNotRemindForOrdinaryUrlsOrNonHttpsSchemes() {
        assertFalse(SensitiveInfoReminder.shouldRemind("https://example.com/news/article"));
        assertFalse(SensitiveInfoReminder.shouldRemind("http://example.com/login"));
        assertFalse(SensitiveInfoReminder.shouldRemind("file:///tmp/otp.txt"));
        assertFalse(SensitiveInfoReminder.shouldRemind(null));
    }

    @Test
    public void catchesEncodedUrlSeparatorsWithoutInspectingPageContent() {
        assertTrue(SensitiveInfoReminder.shouldRemind("https://example.com/path?next=otp%2Fverify"));
        // The class inspects only the supplied URL string, not page title, HTML, forms, or credentials.
        assertFalse(SensitiveInfoReminder.shouldRemind("https://example.com/safe-page"));
    }
}

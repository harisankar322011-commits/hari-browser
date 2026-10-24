package com.haricalcx.haribrowser;

import org.junit.Test;

import static org.junit.Assert.*;

public final class ExternalLinkPolicyTest {
    @Test
    public void acceptsOnlyValidatedMailAndDialTargets() {
        assertEquals(ExternalLinkPolicy.Kind.MAILTO, ExternalLinkPolicy.classify("mailto:hello@example.com"));
        assertEquals("mailto:hello@example.com",
                ExternalLinkPolicy.safeExternalUri("mailto:hello@example.com?subject=ignored"));
        assertEquals(ExternalLinkPolicy.Kind.TEL, ExternalLinkPolicy.classify("tel:+91 (98765) 43210"));
        assertEquals("tel:+919876543210",
                ExternalLinkPolicy.safeExternalUri("tel:+91 (98765) 43210"));
    }

    @Test
    public void rejectsHeaderInjectionAndMalformedMailTargets() {
        for (String value : new String[] {
                "mailto:hello@example.com%0d%0abcc=evil@example.com",
                "mailto:hello@example.com?subject=x%0d%0abcc=evil@example.com",
                "mailto:hello@example.com\r\nBcc: evil@example.com",
                "mailto:not-an-address",
                "mailto:a@@example.com",
                "mailto:a@example..com",
                "mailto:a@example.com,b@example.com"
        }) {
            assertEquals("must reject " + value, ExternalLinkPolicy.Kind.BLOCKED,
                    ExternalLinkPolicy.classify(value));
            assertNull(ExternalLinkPolicy.safeExternalUri(value));
        }
    }

    @Test
    public void rejectsDangerousOrUnsupportedSchemes() {
        for (String value : new String[] {
                "file:///etc/passwd", "intent://evil/#Intent;scheme=https;end",
                "market://details?id=bad", "javascript:alert(1)",
                "content://private/data", "data:text/html,hi", "ftp://example.com/file"
        }) {
            assertEquals("must reject " + value, ExternalLinkPolicy.Kind.BLOCKED,
                    ExternalLinkPolicy.classify(value));
            assertNull(ExternalLinkPolicy.safeExternalUri(value));
        }
    }

    @Test
    public void leavesWebAndPlainSearchInputToExistingNavigationFlow() {
        assertEquals(ExternalLinkPolicy.Kind.HTTPS, ExternalLinkPolicy.classify("https://example.com/a"));
        assertEquals(ExternalLinkPolicy.Kind.HTTP, ExternalLinkPolicy.classify("http://example.com"));
        assertEquals(ExternalLinkPolicy.Kind.NONE, ExternalLinkPolicy.classify("astronomy for beginners"));
        assertEquals(ExternalLinkPolicy.Kind.NONE, ExternalLinkPolicy.classify("example.com"));
    }

    @Test
    public void rejectsInvalidPhoneNumbersAndTooLongInputs() {
        for (String value : new String[] {
                "tel:123", "tel:+91-abc-123456", "tel:++919876543210",
                "tel:123?evil=true", "tel:123#fragment", "tel:" + "1".repeat(128)
        }) {
            assertEquals("must reject " + value, ExternalLinkPolicy.Kind.BLOCKED,
                    ExternalLinkPolicy.classify(value));
        }
    }
}

package com.haricalcx.haribrowser;

import org.junit.Test;

import static org.junit.Assert.*;

public class UrlhausReputationTest {
    @Test
    public void acceptsHttpsAddressWithoutCredentials() {
        assertEquals("https://example.com/path?q=a",
                UrlhausReputation.validateHttpsUrl("https://example.com/path?q=a#local-fragment"));
    }

    @Test
    public void refusesHttpAndInvalidAddresses() {
        assertNull(UrlhausReputation.validateHttpsUrl("http://example.com/malware"));
        assertNull(UrlhausReputation.validateHttpsUrl("not-a-url"));
        assertNull(UrlhausReputation.validateHttpsUrl("https://user:password@example.com/path"));
    }

    @Test
    public void stripsFragmentFromLookupBecauseItIsNotSentToTheWebServer() {
        assertEquals("https://example.com/",
                UrlhausReputation.validateHttpsUrl("https://example.com/#section"));
    }

    @Test
    public void refusesOversizedUrl() {
        assertNull(UrlhausReputation.validateHttpsUrl("https://example.com/" + "a".repeat(4100)));
    }
}

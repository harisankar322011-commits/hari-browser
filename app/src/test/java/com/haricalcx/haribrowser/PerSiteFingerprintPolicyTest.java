package com.haricalcx.haribrowser;

import org.json.JSONArray;
import org.junit.Test;

import static org.junit.Assert.*;

public final class PerSiteFingerprintPolicyTest {
    @Test
    public void togglesOnlyTheExactNormalizedHost() throws Exception {
        String stored = "[]";
        stored = PerSiteFingerprintPolicy.toggle(stored, "Example.COM.");

        assertTrue(PerSiteFingerprintPolicy.isExempt(stored, "example.com"));
        assertTrue(PerSiteFingerprintPolicy.isExempt(stored, "EXAMPLE.COM."));
        assertFalse(PerSiteFingerprintPolicy.isExempt(stored, "cdn.example.com"));
        assertFalse(PerSiteFingerprintPolicy.isExempt(stored, "example.com.attacker"));

        stored = PerSiteFingerprintPolicy.toggle(stored, "example.com");
        assertFalse(PerSiteFingerprintPolicy.isExempt(stored, "example.com"));
        assertEquals(0, new JSONArray(stored).length());
    }

    @Test
    public void malformedAndInvalidEntriesAreIgnored() {
        assertFalse(PerSiteFingerprintPolicy.isExempt("not-json", "example.com"));
        assertFalse(PerSiteFingerprintPolicy.isExempt(
                new JSONArray().put("https://example.com").put("*.example.net").toString(),
                "example.com"));
    }

    @Test
    public void limitsExceptionsAndEvictsTheOldestEntry() throws Exception {
        String stored = "[]";
        for (int i = 0; i < PerSiteFingerprintPolicy.MAX_EXEMPT_HOSTS; i++) {
            stored = PerSiteFingerprintPolicy.toggle(stored, "site" + i + ".example");
        }
        stored = PerSiteFingerprintPolicy.toggle(stored, "new.example");

        JSONArray parsed = new JSONArray(stored);
        assertEquals(PerSiteFingerprintPolicy.MAX_EXEMPT_HOSTS, parsed.length());
        assertFalse(PerSiteFingerprintPolicy.isExempt(stored, "site0.example"));
        assertTrue(PerSiteFingerprintPolicy.isExempt(stored, "site1.example"));
        assertTrue(PerSiteFingerprintPolicy.isExempt(stored, "new.example"));
    }

    @Test
    public void rejectsHostsContainingUrlsPortsWildcardsOrScriptSyntax() {
        for (String invalid : new String[] {
                null, "", "https://example.com", "example.com:443",
                "*.example.com", "example.com/path", "x');alert(1)//",
                "bad..example", "-bad.example", "bad-.example"
        }) {
            assertNull("Expected invalid hostname: " + invalid,
                    PerSiteFingerprintPolicy.normalizeHost(invalid));
        }
        assertEquals("example.com", PerSiteFingerprintPolicy.normalizeHost(" Example.COM. "));
    }
}

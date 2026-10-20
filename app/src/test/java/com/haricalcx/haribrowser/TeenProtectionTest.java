package com.haricalcx.haribrowser;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TeenProtectionTest {
    @Test
    public void blocksExplicitAdultSearchesAndObfuscatedTerms() {
        assertTrue(TeenProtection.isBlockedSearch("free porn videos"));
        assertTrue(TeenProtection.isBlockedSearch("p.o.r.n videos"));
        assertTrue(TeenProtection.isBlockedSearch("h3ntai"));
        assertTrue(TeenProtection.isBlockedSearch("naked girls pictures"));
        assertTrue(TeenProtection.isBlockedSearch("xxx videos"));
    }

    @Test
    public void blocksRealMoneyGamblingSearches() {
        assertTrue(TeenProtection.isBlockedSearch("online casino"));
        assertTrue(TeenProtection.isBlockedSearch("sports betting sites"));
        assertTrue(TeenProtection.isBlockedSearch("real money poker"));
        assertTrue(TeenProtection.isBlockedSearch("best betting apps"));
    }

    @Test
    public void preservesEducationalHealthAndSupportQueries() {
        assertFalse(TeenProtection.isBlockedSearch("sex education in biology"));
        assertFalse(TeenProtection.isBlockedSearch("sexual health for teenagers"));
        assertFalse(TeenProtection.isBlockedSearch("human reproductive system lessons"));
        assertFalse(TeenProtection.isBlockedSearch("porn addiction help"));
        assertFalse(TeenProtection.isBlockedSearch("how online search filters work"));
        assertFalse(TeenProtection.isBlockedSearch("an important research paper"));
    }

    @Test
    public void blocksKnownRestrictedDomainsAndSearchQueries() {
        assertTrue(TeenProtection.isBlockedUrl("https://www.pornhub.com/"));
        assertTrue(TeenProtection.isBlockedUrl("https://www.google.com/search?q=free+porn"));
        assertTrue(TeenProtection.isBlockedUrl("https://www.google.co.in/search?q=p.o.r.n+videos"));
        assertTrue(TeenProtection.isBlockedUrl("https://www.youtube.com/results?search_query=xxx+videos"));
        assertTrue(TeenProtection.isBlockedUrl("https://www.bet365.com/"));
    }

    @Test
    public void allowsNormalSearchesAndDomains() {
        assertFalse(TeenProtection.isBlockedUrl("https://www.google.com/search?q=maths+and+physics"));
        assertFalse(TeenProtection.isBlockedUrl("https://www.google.com/search?q=sexual+health+class"));
        assertFalse(TeenProtection.isBlockedUrl("https://www.nasa.gov/"));
        assertFalse(TeenProtection.isBlockedUrl("https://notpornhub.com/"));
    }

    @Test
    public void hashesAndVerifiesParentPin() {
        String salt = TeenProtection.newPinSalt();
        String hash;
        try {
            hash = TeenProtection.hashPin("4826", salt);
        } catch (Exception error) {
            throw new AssertionError("PIN hashing should succeed", error);
        }
        assertTrue(TeenProtection.verifyPin("4826", salt, hash));
        assertFalse(TeenProtection.verifyPin("4827", salt, hash));
        assertFalse(TeenProtection.verifyPin("4826", salt, ""));
    }
}

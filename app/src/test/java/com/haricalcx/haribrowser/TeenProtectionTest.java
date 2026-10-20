package com.haricalcx.haribrowser;

import org.junit.Test;
import static org.junit.Assert.*;

public class TeenProtectionTest {
    @Test
    public void blocksExplicitSearchesButAllowsLearningAndHealthQueries() {
        assertTrue(TeenProtection.containsBlockedSearchTerm("best porn videos"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("HENTAI art"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("search for leaked nudes"));
        assertFalse(TeenProtection.containsBlockedSearchTerm("sex education for teenagers"));
        assertFalse(TeenProtection.containsBlockedSearchTerm("human reproductive system class 10"));
        assertFalse(TeenProtection.containsBlockedSearchTerm("astronomy for beginners"));
    }

    @Test
    public void extractsAndDecodesSupportedSearchQueries() {
        assertEquals("adult videos", TeenProtection.extractSearchQuery("https://www.google.com/search?q=adult%20videos"));
        assertEquals("porn photos", TeenProtection.extractSearchQuery("https://duckduckgo.com/?q=porn+photos&kp=1"));
        assertEquals("porn", TeenProtection.extractSearchQuery("https://www.startpage.com/sp/search?query=porn"));
        assertNull(TeenProtection.extractSearchQuery("https://example.com/?q=porn"));
    }

    @Test
    public void blocksExplicitTermsInsideSearchProviderUrls() {
        assertTrue(TeenProtection.isBlockedSearchUrl("https://www.google.com/search?q=porn"));
        assertTrue(TeenProtection.isBlockedSearchUrl("https://www.bing.com/search?q=adult%20videos"));
        assertTrue(TeenProtection.isBlockedSearchUrl("https://search.brave.com/search?q=hentai"));
        assertTrue(TeenProtection.isBlockedSearchUrl("https://duckduckgo.com/?q=sex+videos"));
        assertFalse(TeenProtection.isBlockedSearchUrl("https://www.google.com/search?q=sex%20education"));
    }

    @Test
    public void forcesGoogleBingDuckDuckGoAndBraveSafeSearch() {
        assertTrue(TeenProtection.withStrictSafeSearch("https://www.google.com/search?q=math").contains("safe=active"));
        assertTrue(TeenProtection.withStrictSafeSearch("https://www.bing.com/search?q=math").contains("adlt=strict"));
        assertTrue(TeenProtection.withStrictSafeSearch("https://duckduckgo.com/?q=math").contains("kp=1"));
        assertTrue(TeenProtection.withStrictSafeSearch("https://search.brave.com/search?q=math").contains("safesearch=strict"));
    }

    @Test
    public void safeSearchOverridesLessSafeExistingSettingsWithoutDuplicatingParameter() {
        String google = TeenProtection.withStrictSafeSearch("https://www.google.com/search?q=math&safe=off&safe=images");
        assertTrue(google.contains("safe=active"));
        assertFalse(google.contains("safe=off"));
        assertFalse(google.contains("safe=images"));
        assertEquals(1, google.split("safe=active", -1).length - 1);
        String ddg = TeenProtection.withStrictSafeSearch("https://duckduckgo.com/?q=math#results");
        assertTrue(ddg.endsWith("kp=1#results"));
    }

    @Test
    public void doesNotRewriteNonSearchPagesOrUnsupportedProviders() {
        assertEquals("https://example.com/page?q=math", TeenProtection.withStrictSafeSearch("https://example.com/page?q=math"));
        assertEquals("https://www.google.com/", TeenProtection.withStrictSafeSearch("https://www.google.com/"));
        assertEquals("https://www.startpage.com/sp/search?query=math",
                TeenProtection.withStrictSafeSearch("https://www.startpage.com/sp/search?query=math"));
    }

    @Test
    public void parentPinUsesSaltedHashAndVerifiesCorrectly() throws Exception {
        String pin = "482613";
        String salt = TeenProtection.createPinSalt();
        String hash = TeenProtection.hashPin(pin, salt);
        assertNotEquals(pin, hash);
        assertTrue(TeenProtection.verifyPin(pin, salt, hash));
        assertFalse(TeenProtection.verifyPin("482614", salt, hash));
        assertFalse(TeenProtection.verifyPin(pin, TeenProtection.createPinSalt(), hash));
    }
}

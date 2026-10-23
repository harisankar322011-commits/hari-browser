package com.haricalcx.haribrowser;

import org.junit.Test;
import static org.junit.Assert.*;

public class TeenProtectionTest {
    @Test
    public void blocksExplicitSearchesButAllowsLearningAndHealthQueries() {
        assertTrue(TeenProtection.containsBlockedSearchTerm("best porn videos"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("HENTAI art"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("search for leaked nudes"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("p.o.r.n videos"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("h3ntai art"));
        assertFalse(TeenProtection.containsBlockedSearchTerm("sex education for teenagers"));
        assertFalse(TeenProtection.containsBlockedSearchTerm("human reproductive system class 10"));
        assertFalse(TeenProtection.containsBlockedSearchTerm("sexual health for teenagers"));
        assertFalse(TeenProtection.containsBlockedSearchTerm("porn addiction help"));
        assertFalse(TeenProtection.containsBlockedSearchTerm("someone leaked my nudes"));
        assertFalse(TeenProtection.containsBlockedSearchTerm("online gambling addiction help"));
        assertFalse(TeenProtection.containsBlockedSearchTerm("astronomy for beginners"));
    }

    @Test
    public void extractsAndDecodesSupportedSearchQueries() {
        assertEquals("adult videos", TeenProtection.extractSearchQuery("https://www.google.com/search?q=adult%20videos"));
        assertEquals("porn photos", TeenProtection.extractSearchQuery("https://duckduckgo.com/?q=porn+photos&kp=1"));
        assertEquals("porn", TeenProtection.extractSearchQuery("https://www.startpage.com/sp/search?query=porn"));
        assertEquals("adult videos", TeenProtection.extractSearchQuery("https://search.yahoo.com/search?p=adult%20videos"));
        assertEquals("porn", TeenProtection.extractSearchQuery("https://www.ecosia.org/search?q=porn"));
        assertEquals("xxx photos", TeenProtection.extractSearchQuery("https://www.mojeek.com/search?q=xxx+photos"));
        assertEquals("nude", TeenProtection.extractSearchQuery("https://www.qwant.com/?q=nude"));
        assertEquals("casino", TeenProtection.extractSearchQuery("https://www.yandex.com/search/?text=casino"));
        assertEquals("adult videos", TeenProtection.extractSearchQuery("https://www.baidu.com/s?wd=adult%20videos"));
        assertNull(TeenProtection.extractSearchQuery("https://example.com/?q=porn"));
    }

    @Test
    public void blocksExplicitTermsInsideSearchProviderUrls() {
        assertTrue(TeenProtection.isBlockedSearchUrl("https://www.google.com/search?q=porn"));
        assertTrue(TeenProtection.isBlockedSearchUrl("https://www.bing.com/search?q=adult%20videos"));
        assertTrue(TeenProtection.isBlockedSearchUrl("https://search.brave.com/search?q=hentai"));
        assertTrue(TeenProtection.isBlockedSearchUrl("https://duckduckgo.com/?q=sex+videos"));
        assertTrue(TeenProtection.isBlockedSearchUrl("https://www.ecosia.org/search?q=porn"));
        assertTrue(TeenProtection.isBlockedSearchUrl("https://search.yahoo.com/search?p=adult+videos"));
        assertTrue(TeenProtection.isBlockedSearchUrl("https://www.mojeek.com/search?q=hentai"));
        assertTrue(TeenProtection.isBlockedSearchUrl("https://www.qwant.com/?q=xxx"));
        assertTrue(TeenProtection.isBlockedSearchUrl("https://www.yandex.com/search/?text=online+casino"));
        assertTrue(TeenProtection.isBlockedSearchUrl("https://www.baidu.com/s?wd=porn"));
        assertFalse(TeenProtection.isBlockedSearchUrl("https://www.google.com/search?q=sex%20education"));
        assertTrue(TeenProtection.isBlockedSearchUrl("https://www.google.com/search?q=p.o.r.n+videos"));
        assertFalse(TeenProtection.isBlockedSearchUrl("https://www.google.com/search?q=porn+addiction+help"));
    }

    @Test
    public void blocksRestrictedAdultAndGamblingDomainsButNotLookalikeHosts() {
        assertTrue(TeenProtection.isBlockedDomain("https://www.pornhub.com/"));
        assertTrue(TeenProtection.isBlockedDomain("https://www.bet365.com/sports"));
        assertTrue(TeenProtection.isBlockedDomain("https://subdomain.stake.com/"));
        assertFalse(TeenProtection.isBlockedDomain("https://notpornhub.com/"));
        assertFalse(TeenProtection.isBlockedDomain("https://www.nasa.gov/"));
        assertTrue(TeenProtection.isBlockedUrl("https://www.pornhub.com/"));
        assertTrue(TeenProtection.isBlockedUrl("https://www.google.com/search?q=online+casino"));
    }

    @Test
    public void forcesGoogleBingDuckDuckGoBraveAndYahooSafeSearch() {
        assertTrue(TeenProtection.withStrictSafeSearch("https://www.google.com/search?q=math").contains("safe=active"));
        assertTrue(TeenProtection.withStrictSafeSearch("https://www.bing.com/search?q=math").contains("adlt=strict"));
        assertTrue(TeenProtection.withStrictSafeSearch("https://duckduckgo.com/?q=math").contains("kp=1"));
        assertTrue(TeenProtection.withStrictSafeSearch("https://search.brave.com/search?q=math").contains("safesearch=strict"));
        String yahoo = TeenProtection.withStrictSafeSearch("https://search.yahoo.com/search?p=math&vm=off&vm=n");
        assertTrue(yahoo.contains("vm=r"));
        assertFalse(yahoo.contains("vm=off"));
        assertFalse(yahoo.contains("vm=n"));
        assertEquals(1, yahoo.split("vm=r", -1).length - 1);
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
    public void identifiesBraveSearchHostForStrictPreferenceCookie() {
        assertTrue(TeenProtection.isBraveSearchHostUrl("https://search.brave.com/search?q=math"));
        assertTrue(TeenProtection.isBraveSearchHostUrl("https://search.brave.com/"));
        assertFalse(TeenProtection.isBraveSearchHostUrl("https://example.com/search?q=math"));
    }

    @Test
    public void doesNotRewriteNonSearchPagesOrUnsupportedProviders() {
        assertEquals("https://example.com/page?q=math", TeenProtection.withStrictSafeSearch("https://example.com/page?q=math"));
        assertEquals("https://www.google.com/", TeenProtection.withStrictSafeSearch("https://www.google.com/"));
        assertEquals("https://www.startpage.com/sp/search?query=math",
                TeenProtection.withStrictSafeSearch("https://www.startpage.com/sp/search?query=math"));
        assertEquals("https://www.ecosia.org/search?q=math",
                TeenProtection.withStrictSafeSearch("https://www.ecosia.org/search?q=math"));
        assertEquals("https://www.yandex.com/search/?text=math",
                TeenProtection.withStrictSafeSearch("https://www.yandex.com/search/?text=math"));
    }

    @Test
    public void blocksGraphicViolenceDangerousInstructionsAndSelfHarmQueries() {
        assertTrue(TeenProtection.containsBlockedSearchTerm("real gore videos"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("beheading video"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("how to make a pipe bomb"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("h.o.w t.o m.a.k.e a p.i.p.e b.o.m.b"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("buy fentanyl online"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("suicide methods"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("how to cut myself"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("बम कैसे बनाएं"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("ആത്മഹത്യ മാർഗങ്ങൾ"));
    }

    @Test
    public void allowsSafetyEducationPreventionAndHelpSeekingQueries() {
        assertFalse(TeenProtection.containsBlockedSearchTerm("suicide prevention hotline"));
        assertFalse(TeenProtection.containsBlockedSearchTerm("self harm recovery resources"));
        assertFalse(TeenProtection.containsBlockedSearchTerm("how to help a suicidal friend"));
        assertFalse(TeenProtection.containsBlockedSearchTerm("firearm safety laws"));
        assertFalse(TeenProtection.containsBlockedSearchTerm("history of World War II battles"));
        assertFalse(TeenProtection.containsBlockedSearchTerm("drug addiction treatment"));
        assertFalse(TeenProtection.containsBlockedSearchTerm("report a bomb threat"));
        assertFalse(TeenProtection.containsBlockedSearchTerm("overdose emergency help"));
    }

    @Test
    public void appliesDangerousSearchRulesToRecognizedSearchProviderUrls() {
        assertTrue(TeenProtection.isBlockedSearchUrl(
                "https://www.google.com/search?q=suicide+methods"));
        assertTrue(TeenProtection.isBlockedSearchUrl(
                "https://www.bing.com/search?q=buy+fentanyl+online"));
        assertTrue(TeenProtection.isBlockedSearchUrl(
                "https://duckduckgo.com/?q=how+to+make+a+pipe+bomb"));
        assertFalse(TeenProtection.isBlockedSearchUrl(
                "https://www.google.com/search?q=suicide+prevention+hotline"));
    }

    @Test
    public void blocksAdultGamblingAndDangerousQueriesInMoreIndianScriptsAndHinglish() {
        assertTrue(TeenProtection.containsBlockedSearchTerm("অশ্লীল ভিডিও"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("পর্ন ভিডিও"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("अश्लील व्हिडिओ"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("અશ્લીલ વીડિયો"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("ਅਸ਼ਲੀਲ ਵੀਡੀਓ"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("ଅଶ୍ଳୀଳ ଭିଡିଓ"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("ashleel video"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("gandi video"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("nangi tasveer"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("online satta"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("bomb kaise banaye"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("ଆତ୍ମହତ୍ୟା ଉପାୟ"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("બૉમ્બ કેવી રીતે બનાવવો"));
        assertTrue(TeenProtection.containsBlockedSearchTerm("ਖੁਦਕੁਸ਼ੀ ਦੇ ਤਰੀਕੇ"));
    }

    @Test
    public void allowsIndianLanguagePreventionAndRecoveryQueries() {
        assertFalse(TeenProtection.containsBlockedSearchTerm("આપઘાત નિવારણ મદદ"));
        assertFalse(TeenProtection.containsBlockedSearchTerm("আত্মহত্যা প্রতিরোধ সাহায্য"));
        assertFalse(TeenProtection.containsBlockedSearchTerm("ଆତ୍ମହତ୍ୟା ରୋକଥାମ ସହାୟତା"));
        assertFalse(TeenProtection.containsBlockedSearchTerm("आत्महत्या रोकथाम हेल्पलाइन"));
        assertFalse(TeenProtection.containsBlockedSearchTerm("porn ki lat se chutkara"));
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

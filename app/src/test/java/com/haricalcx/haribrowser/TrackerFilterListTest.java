package com.haricalcx.haribrowser;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;

public class TrackerFilterListTest {
    @Test
    public void parsesHostsLinesAndCommonHostsFileMappings() {
        Set<String> hosts = TrackerFilterList.parseHosts(
                "# comments\n0.0.0.0 Ads.Example.com tracker.example\n" +
                "127.0.0.1 pixel.example.net\n" +
                "plain.example.org\n! another comment\nlocalhost localhost\n");

        assertEquals(4, hosts.size());
        assertTrue(hosts.contains("ads.example.com"));
        assertTrue(hosts.contains("tracker.example"));
        assertTrue(hosts.contains("pixel.example.net"));
        assertTrue(hosts.contains("plain.example.org"));
        assertFalse(hosts.contains("localhost"));
    }

    @Test
    public void ignoresMalformedTokensAndUnsupportedAdblockRules() {
        Set<String> hosts = TrackerFilterList.parseHosts(
                "||ads.example.com^\nnot a domain\n.bad.example\nvalid.example.com\n");
        assertEquals(1, hosts.size());
        assertTrue(hosts.contains("valid.example.com"));
    }

    @Test
    public void refusesOversizedListsAndDeduplicatesHosts() {
        assertThrows(IllegalArgumentException.class, () ->
                TrackerFilterList.parseHosts("x".repeat(TrackerFilterList.MAX_TEXT_CHARS + 1)));
        Set<String> hosts = TrackerFilterList.parseHosts(
                "0.0.0.0 duplicate.example\n127.0.0.1 duplicate.example\n");
        assertEquals(1, hosts.size());
        assertThrows(UnsupportedOperationException.class, () -> hosts.add("other.example"));
    }

    @Test
    public void domainValidationRejectsSchemesPortsWildcardsAndInvalidLabels() {
        assertTrue(TrackerFilterList.isDomain("cdn-2.example.co.in"));
        assertFalse(TrackerFilterList.isDomain("https://example.com"));
        assertFalse(TrackerFilterList.isDomain("example.com:443"));
        assertFalse(TrackerFilterList.isDomain("*.example.com"));
        assertFalse(TrackerFilterList.isDomain("-bad.example"));
        assertFalse(TrackerFilterList.isDomain("example..com"));
    }
}

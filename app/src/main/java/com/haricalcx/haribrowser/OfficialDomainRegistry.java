package com.haricalcx.haribrowser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;

/**
 * Offline, curated list of selected Indian public-service and financial domains.
 *
 * <p>A match means only that the hostname is within a domain on this list. It
 * does not certify the TLS connection, page contents, the destination's current
 * status, or a particular transaction. Keep entries specific: never trust every
 * host ending in a broad suffix such as gov.in or co.in.</p>
 */
public final class OfficialDomainRegistry {
    private static final class Entry {
        final String domain;
        final String service;
        final Set<String> brandKeys;

        Entry(String domain, String service, String... brandKeys) {
            this.domain = domain;
            this.service = service;
            this.brandKeys = Collections.unmodifiableSet(
                    new HashSet<>(Arrays.asList(brandKeys)));
        }
    }

    private static final List<Entry> ENTRIES = Collections.unmodifiableList(Arrays.asList(
            new Entry("uidai.gov.in", "UIDAI / Aadhaar", "uidai", "aadhaar"),
            new Entry("digilocker.gov.in", "DigiLocker", "digilocker"),
            new Entry("digitallocker.gov.in", "DigiLocker", "digilocker"),
            new Entry("umang.gov.in", "UMANG", "umang"),
            new Entry("incometax.gov.in", "Income Tax Department", "incometax"),
            new Entry("incometaxindia.gov.in", "Income Tax Department", "incometax"),
            new Entry("gst.gov.in", "GST portal", "gst"),
            new Entry("epfindia.gov.in", "EPFO", "epfo", "epfindia"),
            new Entry("parivahan.gov.in", "Parivahan", "parivahan"),
            new Entry("npscra.nsdl.co.in", "NPS", "nps", "npscra"),
            new Entry("irctc.co.in", "IRCTC", "irctc"),
            new Entry("passportindia.gov.in", "Passport Seva", "passportindia"),
            new Entry("npci.org.in", "NPCI", "npci"),
            new Entry("sbi.co.in", "SBI", "sbi"),
            new Entry("onlinesbi.sbi", "SBI", "sbi", "onlinesbi"),
            new Entry("sbicard.com", "SBI Card", "sbicard"),
            new Entry("sbilife.co.in", "SBI Life", "sbilife"),
            new Entry("sbi-life.com", "SBI Life", "sbi", "sbilife"),
            new Entry("hdfcbank.com", "HDFC Bank", "hdfc", "hdfcbank"),
            new Entry("hdfcsec.com", "HDFC Securities", "hdfcsec"),
            new Entry("hdfclife.com", "HDFC Life", "hdfclife"),
            new Entry("icicibank.com", "ICICI Bank", "icici", "icicibank"),
            new Entry("icicidirect.com", "ICICI Direct", "icicidirect"),
            new Entry("iciciprulife.com", "ICICI Prudential Life", "iciciprulife"),
            new Entry("icicilombard.com", "ICICI Lombard", "icicilombard"),
            new Entry("axisbank.com", "Axis Bank", "axis", "axisbank"),
            new Entry("axismf.com", "Axis Mutual Fund", "axismf"),
            new Entry("diksha.gov.in", "DIKSHA", "diksha"),
            new Entry("swayam.gov.in", "SWAYAM", "swayam")
    ));

    private OfficialDomainRegistry() {}

    /** Returns the listed service for an exact official hostname or its DNS subdomain. */
    public static String serviceForHost(String candidate) {
        Entry entry = findEntry(candidate);
        return entry == null ? null : entry.service;
    }

    /** Returns the matching curated domain, or null when the host is not listed. */
    public static String officialDomainForHost(String candidate) {
        Entry entry = findEntry(candidate);
        return entry == null ? null : entry.domain;
    }

    /**
     * Used by the impersonation heuristic: the official domain must belong to
     * the brand being checked, not merely to some other listed organization.
     */
    public static boolean isOfficialDomainForBrand(String candidate, String brand) {
        if (brand == null || brand.trim().isEmpty()) return false;
        Entry entry = findEntry(candidate);
        return entry != null && entry.brandKeys.contains(brand.toLowerCase(Locale.ROOT));
    }

    /** Whole-label or hyphen-delimited matching; arbitrary substrings are not brand evidence. */
    public static boolean hostContainsBrandToken(String candidate, String brand) {
        String host = normalizeHost(candidate);
        if (host == null || brand == null || brand.trim().isEmpty()) return false;
        String needle = brand.toLowerCase(Locale.ROOT);
        for (String label : host.split("\\.")) {
            if (label.equals(needle)
                    || label.startsWith(needle + "-")
                    || label.endsWith("-" + needle)
                    || label.contains("-" + needle + "-")) {
                return true;
            }
        }
        return false;
    }

    /** Snapshot of the bundled domains for display/tests; the collection is immutable. */
    public static List<String> officialDomains() {
        List<String> domains = new ArrayList<>();
        for (Entry entry : ENTRIES) domains.add(entry.domain);
        return Collections.unmodifiableList(domains);
    }

    private static Entry findEntry(String candidate) {
        String host = normalizeHost(candidate);
        if (host == null) return null;
        Entry best = null;
        for (Entry entry : ENTRIES) {
            if ((host.equals(entry.domain) || host.endsWith("." + entry.domain))
                    && (best == null || entry.domain.length() > best.domain.length())) {
                best = entry;
            }
        }
        return best;
    }

    private static String normalizeHost(String candidate) {
        if (candidate == null) return null;
        String host = candidate.trim().toLowerCase(Locale.ROOT);
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.isEmpty() || host.length() > 253 || host.indexOf('/') >= 0
                || host.indexOf(':') >= 0 || host.indexOf('@') >= 0
                || host.indexOf(' ') >= 0 || host.indexOf('\t') >= 0) return null;
        String[] labels = host.split("\\.", -1);
        if (labels.length < 2) return null;
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63
                    || label.startsWith("-") || label.endsWith("-")) return null;
            for (int i = 0; i < label.length(); i++) {
                char ch = label.charAt(i);
                if (!((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '-')) {
                    return null;
                }
            }
        }
        return host;
    }
}

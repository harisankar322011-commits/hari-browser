package com.haricalcx.haribrowser;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Conservative, local-only URL pattern checks for common Indian impersonation
 * and payment/account-recovery lures. This does not inspect page contents or
 * query a threat-reputation service and must never be treated as a verdict.
 */
public final class IndianScamChecker {
    private IndianScamChecker() {}

    public static List<String> inspect(String value) {
        if (value == null || value.trim().isEmpty()) return Collections.emptyList();
        final URI uri;
        try {
            uri = new URI(value.trim());
        } catch (Exception invalid) {
            return Collections.emptyList();
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            return Collections.emptyList();
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String full = (host + " " + safe(uri.getRawPath()) + " " + safe(uri.getRawQuery()))
                .toLowerCase(Locale.ROOT);
        List<String> warnings = new ArrayList<>();

        checkBrand(host, warnings, "uidai", "uidai.gov.in");
        checkBrand(host, warnings, "aadhaar", "uidai.gov.in");
        checkBrand(host, warnings, "sbi", "sbi.co.in", "onlinesbi.sbi");
        checkBrand(host, warnings, "hdfcbank", "hdfcbank.com");
        checkBrand(host, warnings, "icicibank", "icicibank.com");
        checkBrand(host, warnings, "axisbank", "axisbank.com");
        checkBrand(host, warnings, "npci", "npci.org.in");
        checkBrand(host, warnings, "irctc", "irctc.co.in");
        checkBrand(host, warnings, "incometax", "incometax.gov.in");
        checkBrand(host, warnings, "passportindia", "passportindia.gov.in");
        checkBrand(host, warnings, "gst", "gst.gov.in");

        int sensitive = countAny(full, "otp", "pin", "password", "kyc", "aadhaar",
                "aadhar", "upi", "bank", "account", "wallet");
        int action = countAny(full, "verify", "update", "unlock", "refund", "claim",
                "payment", "login", "expire", "blocked", "urgent", "immediately", "prize", "reward");
        boolean urgency = containsAny(full, "urgent", "immediately", "expire", "blocked", "suspend", "suspended");
        if ((sensitive >= 2 && action >= 1) || (urgency && sensitive >= 1 && action >= 1)) {
            warnings.add("Possible Indian payment, KYC, OTP, or account-scam pattern in the address. This is a URL-only heuristic, not proof that the site is fraudulent.");
        }
        return Collections.unmodifiableList(warnings);
    }

    private static void checkBrand(String host, List<String> warnings, String brand, String... officialDomains) {
        // Avoid treating an arbitrary substring such as "largest" as a GST brand mention.
        if ("gst".equals(brand)) {
            boolean tokenFound = false;
            for (String label : host.split("[.-]")) {
                if ("gst".equals(label)) { tokenFound = true; break; }
            }
            if (!tokenFound) return;
        } else if (!host.contains(brand)) {
            return;
        }
        for (String official : officialDomains) {
            if (host.equals(official) || host.endsWith("." + official)) return;
        }
        warnings.add("The hostname mentions '" + brand + "' but does not end in a recognized official domain for that service. Verify the address independently.");
    }

    private static int countAny(String value, String... tokens) {
        int count = 0;
        for (String token : tokens) if (value.contains(token)) count++;
        return count;
    }

    private static boolean containsAny(String value, String... tokens) {
        return countAny(value, tokens) > 0;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

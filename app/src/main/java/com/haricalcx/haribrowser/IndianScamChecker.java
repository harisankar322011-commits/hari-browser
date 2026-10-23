package com.haricalcx.haribrowser;

import java.net.URI;
import java.util.ArrayList;
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

        // Brand matching and the positive official-domain indicator share one registry.
        checkBrand(host, warnings, "uidai");
        checkBrand(host, warnings, "aadhaar");
        checkBrand(host, warnings, "sbi");
        checkBrand(host, warnings, "sbicard");
        checkBrand(host, warnings, "sbilife");
        checkBrand(host, warnings, "hdfc");
        checkBrand(host, warnings, "hdfcbank");
        checkBrand(host, warnings, "hdfcsec");
        checkBrand(host, warnings, "hdfclife");
        checkBrand(host, warnings, "icici");
        checkBrand(host, warnings, "icicibank");
        checkBrand(host, warnings, "icicidirect");
        checkBrand(host, warnings, "iciciprulife");
        checkBrand(host, warnings, "icicilombard");
        checkBrand(host, warnings, "axis");
        checkBrand(host, warnings, "axisbank");
        checkBrand(host, warnings, "axismf");
        checkBrand(host, warnings, "npci");
        checkBrand(host, warnings, "irctc");
        checkBrand(host, warnings, "incometax");
        checkBrand(host, warnings, "passportindia");
        checkBrand(host, warnings, "gst");
        checkBrand(host, warnings, "digilocker");
        checkBrand(host, warnings, "digitallocker");
        checkBrand(host, warnings, "umang");
        checkBrand(host, warnings, "epfo");
        checkBrand(host, warnings, "parivahan");
        checkBrand(host, warnings, "nps");
        checkBrand(host, warnings, "diksha");
        checkBrand(host, warnings, "swayam");

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

    private static void checkBrand(String host, List<String> warnings, String brand) {
        if (!OfficialDomainRegistry.hostContainsBrandToken(host, brand)) return;
        if (OfficialDomainRegistry.isOfficialDomainForBrand(host, brand)) return;
        warnings.add("The hostname mentions '" + brand + "' but does not match a recognized official domain for that service. Verify the address independently.");
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

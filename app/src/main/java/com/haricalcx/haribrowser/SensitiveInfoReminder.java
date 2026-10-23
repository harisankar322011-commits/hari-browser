package com.haricalcx.haribrowser;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Non-blocking reminder trigger based only on URL components. It does not read
 * page content or infer that a website is malicious; matching common login,
 * OTP, KYC, identity, or verification words only prompts the user to pause.
 */
final class SensitiveInfoReminder {
    private static final Pattern SENSITIVE_URL_TOKEN = Pattern.compile(
            "(?:^|[^a-z0-9])(?:aadhaar|aadhar|otp|e-kyc|ekyc|kyc|netbanking|" +
            "login|log-in|signin|sign-in|verify|verification|identity)(?:$|[^a-z0-9])");

    private SensitiveInfoReminder() {}

    static boolean shouldRemind(String value) {
        if (value == null || value.length() > 4096) return false;
        try {
            URI uri = new URI(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null ||
                    uri.getUserInfo() != null) return false;

            String toCheck = uri.getHost() + " " +
                    (uri.getRawPath() == null ? "" : uri.getRawPath()) + " " +
                    (uri.getRawQuery() == null ? "" : uri.getRawQuery());
            String normalized = toCheck.toLowerCase(Locale.ROOT);
            // Treat encoded separators as separators without decoding arbitrary data.
            normalized = normalized.replace("%2f", "/").replace("%3a", ":")
                    .replace("%3d", "=").replace("%26", "&").replace("%5f", "_")
                    .replace("%2d", "-");
            return SENSITIVE_URL_TOKEN.matcher(normalized).find();
        } catch (Exception invalid) {
            return false;
        }
    }
}

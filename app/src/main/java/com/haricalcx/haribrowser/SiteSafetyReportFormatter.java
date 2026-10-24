package com.haricalcx.haribrowser;

/**
 * Formats the native pre-navigation safety dialog without Android UI dependencies.
 *
 * The report only summarizes URL-pattern checks and optional URLhaus results. It never
 * asserts that a site is safe, and clearly discloses whether the URL was shared online.
 */
final class SiteSafetyReportFormatter {
    private SiteSafetyReportFormatter() {}

    static final class Presentation {
        final String title;
        final String message;
        final String positiveLabel;
        final boolean knownMalware;

        Presentation(String title, String message, String positiveLabel, boolean knownMalware) {
            this.title = title;
            this.message = message;
            this.positiveLabel = positiveLabel;
            this.knownMalware = knownMalware;
        }
    }

    static Presentation format(String url,
                               SiteSafetyChecker.Report report,
                               UrlhausReputation.Result reputation,
                               boolean privateMode,
                               boolean automaticUrlhausEnabled) {
        SiteSafetyChecker.Report effectiveReport =
                report == null ? SiteSafetyChecker.inspect(url) : report;
        StringBuilder message = new StringBuilder();
        message.append("Destination: ").append(effectiveReport.getHost()).append("\n\n");
        message.append("LOCAL URL CHECK\n");
        for (String check : effectiveReport.getPassedChecks()) {
            message.append("✓ ").append(check).append("\n");
        }
        if (!effectiveReport.getWarnings().isEmpty()) {
            message.append("\nREVIEW THESE SIGNALS\n");
            for (String warning : effectiveReport.getWarnings()) {
                message.append("⚠ ").append(warning).append("\n");
            }
        }

        boolean knownMalware = reputation != null && reputation.listed;
        if (reputation != null) {
            message.append("\n\nONLINE URL REPUTATION (URLhaus)\n");
            message.append(reputation.details).append("\n");
            if (knownMalware) {
                message.append("\nWARNING: avoid this URL. It has a URLhaus malware-distribution record.");
            } else if ("NO_MATCH".equals(reputation.status)) {
                message.append("\nNo record does not mean safe. URLhaus does not identify every phishing or scam site.");
            } else {
                message.append("\nThe online check did not complete. The URL has not received a clean verdict.");
            }
        } else if (!privateMode && automaticUrlhausEnabled) {
            message.append("\n\nOnline URL reputation could not be run because no Auth-Key is configured. ");
            message.append("Only the local URL checks below were performed.");
        }

        message.append("\n\nLocal checks run on this device and review URL patterns only; they do not certify the website or inspect all page content. ");
        message.append("India-focused scam checks look for selected impersonation and OTP/KYC/payment wording in the URL only; false positives and missed scams are possible. ");
        if (SensitiveInfoReminder.shouldRemind(url)) {
            message.append("\n\nSENSITIVE INFORMATION REMINDER\n");
            message.append("This address contains login, verification, OTP, KYC, or identity-related words. ");
            message.append("This is a URL-text clue only, not a verdict that the website is unsafe or trustworthy. ");
            message.append("Before entering Aadhaar details or account/financial information, verify the exact hostname independently. ");
            message.append("Never share an OTP, bank PIN, password, or recovery code with a caller, message sender, or support agent.");
        }
        message.append("Android WebView certificate validation and Safe Browsing remain active during connection.");
        if (reputation != null) {
            message.append("\n\nThis URL was sent to URLhaus because online URL reputation is enabled in normal browsing.");
        } else {
            message.append("\n\nThis check did not send the URL to a third-party scanner.");
        }

        String title = knownMalware ? "⚠ Known malware-distribution URL" :
                effectiveReport.hasWarnings() ? "Review site before opening" : "Site safety check";
        String positiveLabel = knownMalware ? "I understand — open anyway" :
                effectiveReport.hasWarnings() ? "Open anyway" : "Open site";
        return new Presentation(title, message.toString(), positiveLabel, knownMalware);
    }
}

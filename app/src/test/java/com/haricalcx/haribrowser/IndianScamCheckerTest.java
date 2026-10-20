package com.haricalcx.haribrowser;

import org.junit.Test;

import static org.junit.Assert.*;

public class IndianScamCheckerTest {
    @Test
    public void suspiciousBankLookalikeAndOtpKycLanguageAreFlagged() {
        String url = "https://secure-sbi-kyc.example/verify?otp=1&urgent=refund";
        String warnings = String.join(" ", IndianScamChecker.inspect(url)).toLowerCase();
        assertTrue(warnings.contains("recognized official domain"));
        assertTrue(warnings.contains("possible indian payment"));
    }

    @Test
    public void recognizedOfficialDomainDoesNotTriggerBrandImpersonation() {
        assertFalse(String.join(" ", IndianScamChecker.inspect("https://onlinesbi.sbi/"))
                .contains("recognized official domain"));
    }

    @Test
    public void unrelatedWebsiteDoesNotGetAnIndianBrandWarning() {
        assertTrue(IndianScamChecker.inspect("https://example.com/about").isEmpty());
    }
}

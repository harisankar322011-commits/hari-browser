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
        assertFalse(containsBrandWarning("https://sbi.co.in/"));
    }

    @Test
    public void legitimateIndianFinancialGroupDomainsAreNotFlaggedAsSbiImpersonators() {
        assertFalse(containsBrandWarning("https://www.sbicard.com/"));
        assertFalse(containsBrandWarning("https://secure.sbicard.com/offers"));
        assertFalse(containsBrandWarning("https://www.sbilife.co.in/"));
        assertFalse(containsBrandWarning("https://secure.sbilife.co.in/policy"));
        assertFalse(containsBrandWarning("https://www.hdfcsec.com/"));
        assertFalse(containsBrandWarning("https://www.axismf.com/"));
    }

    @Test
    public void hyphenDelimitedBrandLookalikesAreFlaggedButArbitrarySubstringsAreNot() {
        assertTrue(containsBrandWarning("https://sbi-login.example/"));
        assertTrue(containsBrandWarning("https://secure-sbicard-login.example/"));
        assertTrue(containsBrandWarning("https://fake-uidai-verify.example/"));
        assertFalse(containsBrandWarning("https://axisbanking.example/"));
        assertFalse(containsBrandWarning("https://myuidaiportal.example/"));
    }

    @Test
    public void officialSubdomainsAreAcceptedButSuffixLookalikesAreNot() {
        assertFalse(containsBrandWarning("https://portal.uidai.gov.in/"));
        assertTrue(containsBrandWarning("https://uidai.gov.in.example/"));
        assertFalse(containsBrandWarning("https://notuidai.gov.in.example/"));
    }

    @Test
    public void recognizedFinancialGroupAliasesAreNotFalsePositives() {
        assertFalse(containsBrandWarning("https://www.sbi-life.com/"));
        assertFalse(containsBrandWarning("https://www.hdfcsec.com/"));
        assertFalse(containsBrandWarning("https://www.icicidirect.com/"));
        assertFalse(containsBrandWarning("https://www.axismf.com/"));
        assertFalse(containsBrandWarning("https://www.digilocker.gov.in/"));
    }

    @Test
    public void scamWarningAndOfficialIndicatorUseTheSameDomainRegistry() {
        assertTrue(containsBrandWarning("https://fake-uidai-verify.example/"));
        assertFalse(containsBrandWarning("https://portal.uidai.gov.in/"));
        assertTrue(OfficialDomainRegistry.isOfficialDomainForBrand("portal.uidai.gov.in", "uidai"));
        assertFalse(OfficialDomainRegistry.isOfficialDomainForBrand("uidai.gov.in.example", "uidai"));
    }

    @Test
    public void unrelatedWebsiteDoesNotGetAnIndianBrandWarning() {
        assertTrue(IndianScamChecker.inspect("https://example.com/about").isEmpty());
    }

    private static boolean containsBrandWarning(String url) {
        for (String warning : IndianScamChecker.inspect(url)) {
            if (warning.contains("recognized official domain")) return true;
        }
        return false;
    }
}

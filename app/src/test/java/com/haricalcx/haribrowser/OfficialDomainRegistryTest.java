package com.haricalcx.haribrowser;

import org.junit.Test;
import java.util.List;

import static org.junit.Assert.*;

public class OfficialDomainRegistryTest {
    @Test
    public void recognizesCuratedGovernmentAndPublicServiceDomains() {
        assertEquals("UIDAI / Aadhaar",
                OfficialDomainRegistry.serviceForHost("www.uidai.gov.in"));
        assertNull(OfficialDomainRegistry.serviceForHost("digitallocker.gov.in"));
        assertEquals("Parivahan",
                OfficialDomainRegistry.serviceForHost("mparivahan.parivahan.gov.in"));
        assertEquals("DIKSHA",
                OfficialDomainRegistry.serviceForHost("diksha.gov.in"));
        assertEquals("SWAYAM",
                OfficialDomainRegistry.serviceForHost("swayam.gov.in"));
    }

    @Test
    public void recognizesDistinctLegitimateFinancialGroupDomains() {
        assertEquals("SBI Card", OfficialDomainRegistry.serviceForHost("www.sbicard.com"));
        assertEquals("SBI Life", OfficialDomainRegistry.serviceForHost("secure.sbilife.co.in"));
        assertNull(OfficialDomainRegistry.serviceForHost("sbi-life.com"));
        assertEquals("HDFC Securities", OfficialDomainRegistry.serviceForHost("www.hdfcsec.com"));
        assertEquals("Axis Mutual Fund", OfficialDomainRegistry.serviceForHost("www.axismf.com"));
    }

    @Test
    public void acceptsRealSubdomainsButRejectsSuffixLookalikes() {
        assertEquals("uidai.gov.in",
                OfficialDomainRegistry.officialDomainForHost("portal.uidai.gov.in"));
        assertNull(OfficialDomainRegistry.serviceForHost("uidai.gov.in.example"));
        assertNull(OfficialDomainRegistry.serviceForHost("notuidai.gov.in.example"));
        assertNull(OfficialDomainRegistry.serviceForHost("example-gst.gov.in.evil"));
    }

    @Test
    public void matchesOnlyTheBrandThatOwnsTheListedDomain() {
        assertTrue(OfficialDomainRegistry.isOfficialDomainForBrand("www.sbicard.com", "sbicard"));
        assertFalse(OfficialDomainRegistry.isOfficialDomainForBrand("www.sbicard.com", "sbi"));
        assertFalse(OfficialDomainRegistry.isOfficialDomainForBrand("www.sbi-life.com", "sbi"));
        assertTrue(OfficialDomainRegistry.isOfficialDomainForBrand("www.hdfcsec.com", "hdfcsec"));
        assertFalse(OfficialDomainRegistry.isOfficialDomainForBrand("hdfcsec.com.example", "hdfcsec"));
    }

    @Test
    public void hostBrandMatchingRequiresWholeLabelsOrHyphenBoundaries() {
        assertTrue(OfficialDomainRegistry.hostContainsBrandToken("secure-sbi-login.example", "sbi"));
        assertTrue(OfficialDomainRegistry.hostContainsBrandToken("uidai-verify.example", "uidai"));
        assertFalse(OfficialDomainRegistry.hostContainsBrandToken("axisbanking.example", "axisbank"));
        assertFalse(OfficialDomainRegistry.hostContainsBrandToken("largest.example", "gst"));
    }

    @Test
    public void rejectsMalformedHostsAndExposesAnImmutableCuratedList() {
        assertNull(OfficialDomainRegistry.serviceForHost("https://uidai.gov.in"));
        assertNull(OfficialDomainRegistry.serviceForHost("uidai.gov.in/path"));
        assertNull(OfficialDomainRegistry.serviceForHost("bad..gov.in"));
        List<String> domains = OfficialDomainRegistry.officialDomains();
        assertTrue(domains.contains("uidai.gov.in"));
        try {
            domains.add("evil.example");
            fail("domain registry snapshot should be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }
}

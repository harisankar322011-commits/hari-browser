package com.haricalcx.haribrowser;

import org.junit.Test;

import static org.junit.Assert.*;

public class CryptographySecurityPolicyTest {
    @Test
    public void permitsOnlyTheExactBundledCryptographyDocument() {
        assertTrue(CryptographySecurityPolicy.isAllowedAssetUrl(
                "https://appassets.androidplatform.net/assets/hari_cryptography.html"));
    }

    @Test
    public void rejectsExternalAndLookalikeOrigins() {
        assertFalse(CryptographySecurityPolicy.isAllowedAssetUrl("https://example.com/"));
        assertFalse(CryptographySecurityPolicy.isAllowedAssetUrl(
                "https://appassets.androidplatform.net.evil.example/assets/hari_cryptography.html"));
        assertFalse(CryptographySecurityPolicy.isAllowedAssetUrl(
                "https://appassets.androidplatform.net@evil.example/assets/hari_cryptography.html"));
        assertFalse(CryptographySecurityPolicy.isAllowedAssetUrl(
                "http://appassets.androidplatform.net/assets/hari_cryptography.html"));
    }

    @Test
    public void rejectsQueriesFragmentsPortsAndAlternatePaths() {
        assertFalse(CryptographySecurityPolicy.isAllowedAssetUrl(
                "https://appassets.androidplatform.net/assets/hari_cryptography.html?x=1"));
        assertFalse(CryptographySecurityPolicy.isAllowedAssetUrl(
                "https://appassets.androidplatform.net/assets/hari_cryptography.html#x"));
        assertFalse(CryptographySecurityPolicy.isAllowedAssetUrl(
                "https://appassets.androidplatform.net:443/assets/hari_cryptography.html"));
        assertFalse(CryptographySecurityPolicy.isAllowedAssetUrl(
                "https://appassets.androidplatform.net/assets/other.html"));
        assertFalse(CryptographySecurityPolicy.isAllowedAssetUrl("not a url"));
        assertFalse(CryptographySecurityPolicy.isAllowedAssetUrl(null));
    }

    @Test
    public void outputFilenameDoesNotContainPathOrControlCharacters() {
        assertEquals(".._notes_.txt",
                CryptographySecurityPolicy.safeFileName("../notes?.txt"));
        assertEquals("hari-output.bin", CryptographySecurityPolicy.safeFileName(".."));
        assertTrue(CryptographySecurityPolicy.safeFileName("a".repeat(250)).length() <= 180);
    }

    @Test
    public void outputMimeTypeStripsParametersAndRejectsInvalidValues() {
        assertEquals("text/plain", CryptographySecurityPolicy.safeMimeType("text/plain;charset=utf-8"));
        assertEquals("application/octet-stream", CryptographySecurityPolicy.safeMimeType("nope"));
        assertEquals("application/octet-stream", CryptographySecurityPolicy.safeMimeType(null));
    }
}
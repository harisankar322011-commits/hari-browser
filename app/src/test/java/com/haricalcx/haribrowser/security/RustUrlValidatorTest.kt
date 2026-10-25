package com.haricalcx.haribrowser.security

import org.junit.Assert.assertEquals
import org.junit.Test

class RustUrlValidatorTest {
    @Test
    fun classifiesHttpsUrl() {
        assertEquals(
            UrlSchemeClassification.HTTPS,
            RustUrlValidator.classify("https://example.com/path").scheme
        )
    }

    @Test
    fun classifiesHttpUrlWithoutCallingItSecure() {
        assertEquals(
            UrlSchemeClassification.HTTP,
            RustUrlValidator.classify("http://example.com").scheme
        )
    }

    @Test
    fun classifiesOtherAbsoluteSchemesSeparately() {
        assertEquals(
            UrlSchemeClassification.OTHER,
            RustUrlValidator.classify("mailto:hello@example.com").scheme
        )
    }

    @Test
    fun acceptsUnicodeDomainNamesForIndianAndInternationalSites() {
        assertEquals(
            UrlSchemeClassification.HTTPS,
            RustUrlValidator.classify("https://пример.рф/path").scheme
        )
    }

    @Test
    fun rejectsMalformedOrWhitespacePaddedInput() {
        assertEquals(
            UrlSchemeClassification.INVALID,
            RustUrlValidator.classify("https://").scheme
        )
        assertEquals(
            UrlSchemeClassification.INVALID,
            RustUrlValidator.classify(" https://example.com").scheme
        )
        assertEquals(
            UrlSchemeClassification.INVALID,
            RustUrlValidator.classify("not a URL").scheme
        )
    }

    @Test
    fun rejectsAmbiguousHttpSyntaxAndInvalidPorts() {
        assertEquals(
            UrlSchemeClassification.INVALID,
            RustUrlValidator.classify("https:example.com").scheme
        )
        assertEquals(
            UrlSchemeClassification.INVALID,
            RustUrlValidator.classify("https://example.com:").scheme
        )
        assertEquals(
            UrlSchemeClassification.INVALID,
            RustUrlValidator.classify("https://example.com:65536/").scheme
        )
    }

    @Test
    fun rejectsMalformedPercentEscapes() {
        assertEquals(
            UrlSchemeClassification.INVALID,
            RustUrlValidator.classify("https://example.com/%ZZ").scheme
        )
        assertEquals(
            UrlSchemeClassification.INVALID,
            RustUrlValidator.classify("https://example.com/%").scheme
        )
    }

    @Test
    fun rejectsOversizedUrls() {
        val oversized = "https://example.com/" + "a".repeat(8200)
        assertEquals(
            UrlSchemeClassification.INVALID,
            RustUrlValidator.classify(oversized).scheme
        )
    }
}

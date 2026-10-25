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
}

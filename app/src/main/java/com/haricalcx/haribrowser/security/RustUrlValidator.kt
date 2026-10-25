package com.haricalcx.haribrowser.security

import java.net.IDN
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * Strict URL syntax/scheme classification for HARI Browser.
 *
 * This is a navigation-input guard, not a reputation, TLS, phishing, malware,
 * or safety verdict. HTTPS only describes the requested scheme.
 */
enum class UrlSchemeClassification {
    HTTPS,
    HTTP,
    OTHER,
    INVALID
}

enum class UrlClassifierBackend {
    RUST_NATIVE,
    KOTLIN_FALLBACK
}

data class UrlClassification(
    val scheme: UrlSchemeClassification,
    val backend: UrlClassifierBackend
)

/**
 * Uses Rust JNI when available, with Kotlin enforcing the same strict input
 * boundary. If the Rust and Kotlin parsers disagree, the result fails closed
 * as INVALID rather than allowing parser-differential ambiguity.
 */
object RustUrlValidator {
    private const val MAX_URL_BYTES = 8_192
    private val nativeBridge: RustUrlNativeBridge? = loadNativeBridge()

    fun classify(rawUrl: String): UrlClassification {
        val kotlinScheme = classifyWithKotlin(rawUrl)
        val bridge = nativeBridge
        if (bridge != null) {
            val nativeScheme = try {
                schemeFromNativeCode(bridge.classifyUrl(rawUrl))
            } catch (_: UnsatisfiedLinkError) {
                null
            } catch (_: SecurityException) {
                null
            }

            if (nativeScheme != null) {
                val result = if (nativeScheme == kotlinScheme) {
                    nativeScheme
                } else {
                    // Parser disagreement is not evidence that the input is safe.
                    UrlSchemeClassification.INVALID
                }
                return UrlClassification(result, UrlClassifierBackend.RUST_NATIVE)
            }
        }

        return UrlClassification(kotlinScheme, UrlClassifierBackend.KOTLIN_FALLBACK)
    }

    private fun loadNativeBridge(): RustUrlNativeBridge? {
        return try {
            System.loadLibrary("hari_url_guard")
            RustUrlNativeBridge()
        } catch (_: UnsatisfiedLinkError) {
            null
        } catch (_: SecurityException) {
            null
        }
    }

    private fun schemeFromNativeCode(code: Int): UrlSchemeClassification? = when (code) {
        0 -> UrlSchemeClassification.INVALID
        1 -> UrlSchemeClassification.HTTPS
        2 -> UrlSchemeClassification.HTTP
        3 -> UrlSchemeClassification.OTHER
        else -> null
    }

    private fun classifyWithKotlin(rawUrl: String): UrlSchemeClassification {
        if (rawUrl.isEmpty() ||
            rawUrl.toByteArray(Charsets.UTF_8).size > MAX_URL_BYTES ||
            rawUrl != rawUrl.trim() ||
            rawUrl.any { it.isWhitespace() || it.code <= 0x1f || it.code == 0x7f } ||
            !hasValidPercentEscapes(rawUrl)
        ) {
            return UrlSchemeClassification.INVALID
        }

        val uri = try {
            URI(rawUrl)
        } catch (_: URISyntaxException) {
            return UrlSchemeClassification.INVALID
        }

        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return UrlSchemeClassification.INVALID

        return when (scheme) {
            "https" -> if (uri.isAbsolute && hasValidHttpAuthority(uri)) {
                UrlSchemeClassification.HTTPS
            } else {
                UrlSchemeClassification.INVALID
            }
            "http" -> if (uri.isAbsolute && hasValidHttpAuthority(uri)) {
                UrlSchemeClassification.HTTP
            } else {
                UrlSchemeClassification.INVALID
            }
            else -> if (uri.isAbsolute) {
                UrlSchemeClassification.OTHER
            } else {
                UrlSchemeClassification.INVALID
            }
        }
    }

    private fun hasValidPercentEscapes(value: String): Boolean {
        var index = 0
        while (index < value.length) {
            if (value[index] == '%') {
                if (index + 2 >= value.length ||
                    value[index + 1].digitToIntOrNull(16) == null ||
                    value[index + 2].digitToIntOrNull(16) == null
                ) {
                    return false
                }
                index += 3
            } else {
                index++
            }
        }
        return true
    }

    private fun hasValidHttpAuthority(uri: URI): Boolean {
        val authority = uri.rawAuthority ?: return false
        if (authority.isEmpty()) return false

        // Credentials are syntactically possible here; SiteSafetyChecker separately warns on them.
        val hostPort = authority.substringAfterLast('@')
        if (hostPort.isEmpty()) return false

        if (hostPort.startsWith("[")) {
            val closingBracket = hostPort.indexOf(']')
            if (closingBracket <= 1) return false
            val ipv6Text = hostPort.substring(1, closingBracket)
            if (ipv6Text.count { it == ':' } < 2 ||
                ipv6Text.any { it !in "0123456789abcdefABCDEF:." }
            ) {
                return false
            }
            val suffix = hostPort.substring(closingBracket + 1)
            return suffix.isEmpty() ||
                (suffix.startsWith(":") && isValidPort(suffix.substring(1)))
        }

        val colon = hostPort.lastIndexOf(':')
        val host = if (colon >= 0) {
            if (hostPort.indexOf(':') != colon ||
                !isValidPort(hostPort.substring(colon + 1))
            ) {
                return false
            }
            hostPort.substring(0, colon)
        } else {
            hostPort
        }
        if (host.isEmpty() || host.any { it.isWhitespace() || it == '%' || it == '/' || it == '\\' }) {
            return false
        }

        val asciiHost = try {
            IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES)
        } catch (_: IllegalArgumentException) {
            return false
        }
        val normalizedHost = asciiHost.removeSuffix(".")
        if (normalizedHost.isEmpty() || normalizedHost.length > 253) return false
        return normalizedHost.split('.').all { label ->
            label.isNotEmpty() && label.length <= 63
        }
    }

    private fun isValidPort(value: String): Boolean {
        if (value.isEmpty() || !value.all { it in '0'..'9' }) return false
        val port = value.toIntOrNull() ?: return false
        return port in 0..65_535
    }
}

/** JNI entry point implemented by rust/hari_url_guard when native builds are enabled. */
internal class RustUrlNativeBridge {
    external fun classifyUrl(rawUrl: String): Int
}

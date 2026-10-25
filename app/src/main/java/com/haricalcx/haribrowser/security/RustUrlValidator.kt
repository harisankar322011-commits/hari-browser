package com.haricalcx.haribrowser.security

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * Experimental URL-scheme classification for the Kotlin/Rust integration pilot.
 *
 * This class only classifies URL syntax/schemes. It does not assess reputation,
 * TLS, phishing, malware, or whether it is safe to open a destination.
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
 * Tries the native Rust classifier when the optional JNI library is packaged.
 * Ordinary builds remain functional without the native library by using a
 * conservative Kotlin syntax fallback.
 */
object RustUrlValidator {
    private val nativeBridge: RustUrlNativeBridge? = loadNativeBridge()

    fun classify(rawUrl: String): UrlClassification {
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
                return UrlClassification(nativeScheme, UrlClassifierBackend.RUST_NATIVE)
            }
        }

        return UrlClassification(classifyWithKotlin(rawUrl), UrlClassifierBackend.KOTLIN_FALLBACK)
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
            rawUrl != rawUrl.trim() ||
            rawUrl.any { it.isWhitespace() || it.code <= 0x1f || it.code == 0x7f }
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
            "https" -> if (uri.isAbsolute && !uri.rawAuthority.isNullOrBlank() &&
                !uri.host.isNullOrBlank()
            ) {
                UrlSchemeClassification.HTTPS
            } else {
                UrlSchemeClassification.INVALID
            }
            "http" -> if (uri.isAbsolute && !uri.rawAuthority.isNullOrBlank() &&
                !uri.host.isNullOrBlank()
            ) {
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
}

/** JNI entry point implemented by rust/hari_url_guard when native builds are enabled. */
internal class RustUrlNativeBridge {
    external fun classifyUrl(rawUrl: String): Int
}

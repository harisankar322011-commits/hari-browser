use jni::objects::{JObject, JString};
use jni::sys::jint;
use jni::JNIEnv;
use url::Url;

const INVALID: jint = 0;
const HTTPS: jint = 1;
const HTTP: jint = 2;
const OTHER: jint = 3;

/// Classifies URL syntax only; it is not a trust or malware verdict.
///
/// Return values: 0 = invalid, 1 = HTTPS, 2 = HTTP, 3 = another absolute URL
/// scheme. Reject control/whitespace characters before the URL parser can
/// normalize them so the Kotlin and Rust interfaces remain deliberately strict.
pub fn classify_url(raw_url: &str) -> jint {
    if raw_url.is_empty()
        || raw_url.trim() != raw_url
        || raw_url
            .chars()
            .any(|character| character.is_whitespace() || character.is_control())
    {
        return INVALID;
    }

    let parsed = match Url::parse(raw_url) {
        Ok(url) => url,
        Err(_) => return INVALID,
    };

    match parsed.scheme() {
        "https" if parsed.host_str().is_some() => HTTPS,
        "http" if parsed.host_str().is_some() => HTTP,
        scheme if !scheme.is_empty() => OTHER,
        _ => INVALID,
    }
}

/// JNI implementation for RustUrlNativeBridge.classifyUrl(String).
///
/// Keep this component isolated until Android-device runtime tests confirm
/// that Kotlin/JNI signatures, packaging, and supported ABIs work as expected.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_haricalcx_haribrowser_security_RustUrlNativeBridge_classifyUrl(
    mut env: JNIEnv<'_>,
    _this: JObject<'_>,
    raw_url: JString<'_>,
) -> jint {
    let java_string = match env.get_string(&raw_url) {
        Ok(value) => value,
        Err(_) => return INVALID,
    };

    let raw_url: String = java_string.into();
    classify_url(&raw_url)
}

#[cfg(test)]
mod tests {
    use super::{classify_url, HTTP, HTTPS, INVALID, OTHER};

    #[test]
    fn classifies_https_with_host() {
        assert_eq!(HTTPS, classify_url("https://example.com/path?q=1"));
    }

    #[test]
    fn classifies_http_as_http_not_https() {
        assert_eq!(HTTP, classify_url("http://example.com"));
    }

    #[test]
    fn classifies_non_http_absolute_schemes_as_other() {
        assert_eq!(OTHER, classify_url("mailto:hello@example.com"));
    }

    #[test]
    fn rejects_incomplete_authorities_and_whitespace() {
        assert_eq!(INVALID, classify_url("https://"));
        assert_eq!(INVALID, classify_url(" https://example.com"));
        assert_eq!(INVALID, classify_url("https://example.com/a b"));
        assert_eq!(INVALID, classify_url(""));
    }
}

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
/// scheme. Reject ambiguous HTTP(S) forms, malformed percent escapes, and
/// excessive input before the URL parser can normalize them.
pub fn classify_url(raw_url: &str) -> jint {
    if raw_url.is_empty()
        || raw_url.len() > 8_192
        || raw_url.trim() != raw_url
        || raw_url
            .chars()
            .any(|character| character.is_whitespace() || character.is_control())
        || !has_valid_percent_escapes(raw_url)
    {
        return INVALID;
    }

    let parsed = match Url::parse(raw_url) {
        Ok(url) => url,
        Err(_) => return INVALID,
    };

    match parsed.scheme() {
        "https" if has_strict_web_authority(raw_url, "https") && parsed.host_str().is_some() => {
            HTTPS
        }
        "http" if has_strict_web_authority(raw_url, "http") && parsed.host_str().is_some() => HTTP,
        "http" | "https" => INVALID,
        scheme if !scheme.is_empty() => OTHER,
        _ => INVALID,
    }
}

fn has_valid_percent_escapes(value: &str) -> bool {
    let bytes = value.as_bytes();
    let mut index = 0;
    while index < bytes.len() {
        if bytes[index] == b'%' {
            if index + 2 >= bytes.len()
                || !bytes[index + 1].is_ascii_hexdigit()
                || !bytes[index + 2].is_ascii_hexdigit()
            {
                return false;
            }
            index += 3;
        } else {
            index += 1;
        }
    }
    true
}

/// Require an explicit // authority and reject empty, malformed, or out-of-range ports.
fn has_strict_web_authority(raw_url: &str, expected_scheme: &str) -> bool {
    let Some((raw_scheme, remainder)) = raw_url.split_once("://") else {
        return false;
    };
    if !raw_scheme.eq_ignore_ascii_case(expected_scheme) {
        return false;
    }

    let authority = remainder
        .split(['/', '?', '#'])
        .next()
        .unwrap_or_default();
    if authority.is_empty() {
        return false;
    }

    let host_port = authority.rsplit('@').next().unwrap_or_default();
    if host_port.is_empty() {
        return false;
    }

    if host_port.starts_with('[') {
        let Some(closing_bracket) = host_port.find(']') else {
            return false;
        };
        if closing_bracket <= 1 {
            return false;
        }
        let suffix = &host_port[closing_bracket + 1..];
        if suffix.is_empty() {
            return true;
        }
        if let Some(port_text) = suffix.strip_prefix(':') {
            return valid_port(port_text);
        }
        return false;
    }

    if let Some((host, port_text)) = host_port.rsplit_once(':') {
        if host.contains(':') || host.is_empty() || !valid_port(port_text) {
            return false;
        }
    }
    !host_port.ends_with(':')
}

fn valid_port(value: &str) -> bool {
    !value.is_empty()
        && value.bytes().all(|byte| byte.is_ascii_digit())
        && value.parse::<u32>().is_ok_and(|port| port <= u16::MAX as u32)
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
    fn accepts_unicode_domain_names() {
        assert_eq!(HTTPS, classify_url("https://пример.рф/path"));
    }

    #[test]
    fn rejects_incomplete_authorities_and_whitespace() {
        assert_eq!(INVALID, classify_url("https://"));
        assert_eq!(INVALID, classify_url(" https://example.com"));
        assert_eq!(INVALID, classify_url("https://example.com/a b"));
        assert_eq!(INVALID, classify_url(""));
    }

    #[test]
    fn rejects_ambiguous_http_syntax_and_invalid_ports() {
        assert_eq!(INVALID, classify_url("https:example.com"));
        assert_eq!(INVALID, classify_url("https://example.com:"));
        assert_eq!(INVALID, classify_url("https://example.com:65536/"));
    }

    #[test]
    fn rejects_malformed_percent_escapes() {
        assert_eq!(INVALID, classify_url("https://example.com/%ZZ"));
        assert_eq!(INVALID, classify_url("https://example.com/%"));
    }

    #[test]
    fn rejects_oversized_urls() {
        let oversized = format!("https://example.com/{}", "a".repeat(8_200));
        assert_eq!(INVALID, classify_url(&oversized));
    }
}

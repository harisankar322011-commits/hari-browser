//! HARI Engine Core — isolated experimental foundation.
//!
//! This crate is deliberately not wired into the production browser yet.
//! Input validation is a narrow boundary check, not a complete HTML parser,
//! sanitizer, malware detector, or browser security verdict.

/// Resource limits applied before a document enters future parser stages.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct EngineLimits {
    /// Maximum accepted input size in bytes.
    pub max_document_bytes: usize,
}

impl Default for EngineLimits {
    fn default() -> Self {
        Self {
            max_document_bytes: 1_048_576,
        }
    }
}

/// A reason a byte sequence was refused at the core input boundary.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum InputError {
    DocumentTooLarge,
    InvalidUtf8,
    ForbiddenControlCharacter,
}

/// Validates size, UTF-8 encoding, and disallowed control characters.
///
/// Newline, carriage return, and tab are allowed because they are common in
/// HTML source. The returned string borrows the original bytes; no copy is
/// made. This function does not parse HTML or make a security judgement.
pub fn validate_document_input<'a>(
    bytes: &'a [u8],
    limits: &EngineLimits,
) -> Result<&'a str, InputError> {
    if bytes.len() > limits.max_document_bytes {
        return Err(InputError::DocumentTooLarge);
    }

    let source = std::str::from_utf8(bytes).map_err(|_| InputError::InvalidUtf8)?;

    if source
        .chars()
        .any(|character| character.is_control() && !matches!(character, '\n' | '\r' | '\t'))
    {
        return Err(InputError::ForbiddenControlCharacter);
    }

    Ok(source)
}

#[cfg(test)]
mod tests {
    use super::{EngineLimits, InputError, validate_document_input};

    #[test]
    fn accepts_small_utf8_html_document() {
        let html = b"<!doctype html><title>HARI</title>";
        assert_eq!(
            validate_document_input(html, &EngineLimits::default()),
            Ok("<!doctype html><title>HARI</title>")
        );
    }

    #[test]
    fn rejects_document_over_configured_byte_limit() {
        let limits = EngineLimits {
            max_document_bytes: 4,
        };
        assert_eq!(
            validate_document_input(b"<html>", &limits),
            Err(InputError::DocumentTooLarge)
        );
    }

    #[test]
    fn rejects_invalid_utf8() {
        assert_eq!(
            validate_document_input(&[0x66, 0x80], &EngineLimits::default()),
            Err(InputError::InvalidUtf8)
        );
    }

    #[test]
    fn rejects_nul_bytes() {
        assert_eq!(
            validate_document_input(b"<html>\0</html>", &EngineLimits::default()),
            Err(InputError::ForbiddenControlCharacter)
        );
    }

    #[test]
    fn permits_html_whitespace_controls() {
        assert!(validate_document_input(b"<html>\n\t\r</html>", &EngineLimits::default()).is_ok());
    }
}

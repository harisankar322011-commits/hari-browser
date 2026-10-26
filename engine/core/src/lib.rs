//! HARI Engine Core — isolated experimental foundation.
//!
//! This crate is deliberately not wired into the production browser yet.

#[cfg(test)]
mod tests {
    use super::{validate_document_input, EngineLimits, InputError};

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
        let limits = EngineLimits { max_document_bytes: 4 };
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

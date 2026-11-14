//! Structured, read-only interpretation of HARI's bounded raw DOCTYPE node.

/// The fields exposed by an HTML DOCTYPE token.
///
/// force_quirks is the tokenizer's force-quirks flag. It is not, by itself,
/// the document's final quirks-mode classification.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DoctypeInfo {
    /// The DOCTYPE name, normalized to ASCII lowercase.
    pub name: String,
    /// The optional PUBLIC identifier, without its surrounding quotes.
    pub public_identifier: Option<String>,
    /// The optional SYSTEM identifier, without its surrounding quotes.
    pub system_identifier: Option<String>,
    /// Whether malformed or incomplete token input requires the force-quirks flag.
    pub force_quirks: bool,
}

/// The compatibility mode indicated by a DOCTYPE token.
///
/// This is a token-level classification. Applying it to a document also depends
/// on parser context, including whether the document is an iframe srcdoc document
/// and whether the parser is allowed to change the mode flag.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DoctypeMode {
    /// Standards mode.
    NoQuirks,
    /// The legacy mode with a limited set of compatibility behavior.
    LimitedQuirks,
    /// Full legacy compatibility mode.
    Quirks,
}

const QUIRKS_PUBLIC_IDENTIFIER_EXACT: &[&str] = &[
    "-//W3O//DTD W3 HTML Strict 3.0//EN//",
    "-/W3C/DTD HTML 4.0 Transitional/EN",
    "HTML",
];

const QUIRKS_SYSTEM_IDENTIFIER_EXACT: &str =
    "http://www.ibm.com/data/dtd/v11/ibmxhtml1-transitional.dtd";

const QUIRKS_PUBLIC_IDENTIFIER_PREFIXES: &[&str] = &[
    "+//Silmaril//dtd html Pro v0r11 19970101//",
    "-//AS//DTD HTML 3.0 asWedit + extensions//",
    "-//AdvaSoft Ltd//DTD HTML 3.0 asWedit + extensions//",
    "-//IETF//DTD HTML 2.0 Level 1//",
    "-//IETF//DTD HTML 2.0 Level 2//",
    "-//IETF//DTD HTML 2.0 Strict Level 1//",
    "-//IETF//DTD HTML 2.0 Strict Level 2//",
    "-//IETF//DTD HTML 2.0 Strict//",
    "-//IETF//DTD HTML 2.0//",
    "-//IETF//DTD HTML 2.1E//",
    "-//IETF//DTD HTML 3.0//",
    "-//IETF//DTD HTML 3.2 Final//",
    "-//IETF//DTD HTML 3.2//",
    "-//IETF//DTD HTML 3//",
    "-//IETF//DTD HTML Level 0//",
    "-//IETF//DTD HTML Level 1//",
    "-//IETF//DTD HTML Level 2//",
    "-//IETF//DTD HTML Level 3//",
    "-//IETF//DTD HTML Strict Level 0//",
    "-//IETF//DTD HTML Strict Level 1//",
    "-//IETF//DTD HTML Strict Level 2//",
    "-//IETF//DTD HTML Strict Level 3//",
    "-//IETF//DTD HTML Strict//",
    "-//IETF//DTD HTML//",
    "-//Metrius//DTD Metrius Presentational//",
    "-//Microsoft//DTD Internet Explorer 2.0 HTML Strict//",
    "-//Microsoft//DTD Internet Explorer 2.0 HTML//",
    "-//Microsoft//DTD Internet Explorer 2.0 Tables//",
    "-//Microsoft//DTD Internet Explorer 3.0 HTML Strict//",
    "-//Microsoft//DTD Internet Explorer 3.0 HTML//",
    "-//Microsoft//DTD Internet Explorer 3.0 Tables//",
    "-//Netscape Comm. Corp.//DTD HTML//",
    "-//Netscape Comm. Corp.//DTD Strict HTML//",
    "-//O'Reilly and Associates//DTD HTML 2.0//",
    "-//O'Reilly and Associates//DTD HTML Extended 1.0//",
    "-//O'Reilly and Associates//DTD HTML Extended Relaxed 1.0//",
    "-//SQ//DTD HTML 2.0 HoTMetaL + extensions//",
    "-//SoftQuad Software//DTD HoTMetaL PRO 6.0::19990601::extensions to HTML 4.0//",
    "-//SoftQuad//DTD HoTMetaL PRO 4.0::19971010::extensions to HTML 4.0//",
    "-//Spyglass//DTD HTML 2.0 Extended//",
    "-//Sun Microsystems Corp.//DTD HotJava HTML//",
    "-//Sun Microsystems Corp.//DTD HotJava Strict HTML//",
    "-//W3C//DTD HTML 3 1995-03-24//",
    "-//W3C//DTD HTML 3.2 Draft//",
    "-//W3C//DTD HTML 3.2 Final//",
    "-//W3C//DTD HTML 3.2//",
    "-//W3C//DTD HTML 3.2S Draft//",
    "-//W3C//DTD HTML Experimental 19960712//",
    "-//W3C//DTD HTML Experimental 970421//",
    "-//W3C//DTD W3 HTML//",
    "-//W3O//DTD W3 HTML 3.0//",
    "-//WebTechs//DTD Mozilla HTML 2.0//",
    "-//WebTechs//DTD Mozilla HTML//",
];

const LIMITED_QUIRKS_PUBLIC_IDENTIFIER_PREFIXES: &[&str] = &[
    "-//W3C//DTD XHTML 1.0 Frameset//",
    "-//W3C//DTD XHTML 1.0 Transitional//",
];

const HTML_4_01_LIMITED_QUIRKS_PUBLIC_IDENTIFIER_PREFIXES: &[&str] = &[
    "-//W3C//DTD HTML 4.01 Frameset//",
    "-//W3C//DTD HTML 4.01 Transitional//",
];

impl DoctypeInfo {
    pub(crate) fn from_raw(raw: &str) -> Self {
        let Some(after_keyword) = raw.strip_prefix("DOCTYPE") else {
            return Self::malformed();
        };
        let bytes = after_keyword.as_bytes();
        let mut cursor = 0;

        skip_whitespace(bytes, &mut cursor);
        let name_start = cursor;
        while bytes.get(cursor).is_some_and(|byte| !is_whitespace(*byte)) {
            cursor += 1;
        }

        let name = after_keyword[name_start..cursor].to_ascii_lowercase();
        if name.is_empty() {
            return Self::new(name, None, None, true);
        }

        skip_whitespace(bytes, &mut cursor);
        if cursor == bytes.len() {
            return Self::new(name, None, None, false);
        }

        let keyword_start = cursor;
        while bytes.get(cursor).is_some_and(u8::is_ascii_alphabetic) {
            cursor += 1;
        }
        let keyword = &bytes[keyword_start..cursor];

        if keyword.eq_ignore_ascii_case(b"public") {
            skip_whitespace(bytes, &mut cursor);
            let Some((public_identifier, terminated)) = read_identifier(after_keyword, &mut cursor)
            else {
                return Self::new(name, None, None, true);
            };
            if !terminated {
                return Self::new(name, Some(public_identifier), None, true);
            }

            skip_whitespace(bytes, &mut cursor);
            if cursor == bytes.len() {
                return Self::new(name, Some(public_identifier), None, false);
            }

            if !matches!(bytes.get(cursor), Some(b'\'' | b'"')) {
                return Self::new(name, Some(public_identifier), None, true);
            }

            // The HTML tokenizer accepts an adjacent quoted SYSTEM identifier
            // after a PUBLIC identifier, while reporting a missing-whitespace
            // parse error. That parse error does not set force-quirks.
            let (system_identifier, terminated) =
                read_identifier(after_keyword, &mut cursor).expect("quote was checked above");
            let force_quirks = !terminated;
            return Self::new(
                name,
                Some(public_identifier),
                Some(system_identifier),
                force_quirks,
            );
        }

        if keyword.eq_ignore_ascii_case(b"system") {
            skip_whitespace(bytes, &mut cursor);
            let Some((system_identifier, terminated)) = read_identifier(after_keyword, &mut cursor)
            else {
                return Self::new(name, None, None, true);
            };
            skip_whitespace(bytes, &mut cursor);
            return Self::new(
                name,
                None,
                Some(system_identifier),
                !terminated,
            );
        }

        Self::new(name, None, None, true)
    }

    /// Classifies this DOCTYPE token using the WHATWG initial-insertion-mode
    /// quirks and limited-quirks identifier lists.
    ///
    /// This does not set a mode on a parsed document. Callers must also account
    /// for parser context, including iframe srcdoc and the parser-cannot-change-
    /// mode flag, before applying the result to a document.
    pub fn compatibility_mode(&self) -> DoctypeMode {
        if self.force_quirks || !self.name.eq_ignore_ascii_case("html") {
            return DoctypeMode::Quirks;
        }

        let public_identifier = self.public_identifier.as_deref();
        let system_identifier = self.system_identifier.as_deref();

        if public_identifier.is_some_and(|identifier| {
            QUIRKS_PUBLIC_IDENTIFIER_EXACT
                .iter()
                .any(|candidate| identifier.eq_ignore_ascii_case(candidate))
                || starts_with_any_ascii_case_insensitive(
                    identifier,
                    QUIRKS_PUBLIC_IDENTIFIER_PREFIXES,
                )
        }) || system_identifier.is_some_and(|identifier| {
            identifier.eq_ignore_ascii_case(QUIRKS_SYSTEM_IDENTIFIER_EXACT)
        }) {
            return DoctypeMode::Quirks;
        }

        if public_identifier.is_some_and(|identifier| {
            starts_with_any_ascii_case_insensitive(
                identifier,
                LIMITED_QUIRKS_PUBLIC_IDENTIFIER_PREFIXES,
            )
        }) {
            return DoctypeMode::LimitedQuirks;
        }

        if public_identifier.is_some_and(|identifier| {
            starts_with_any_ascii_case_insensitive(
                identifier,
                HTML_4_01_LIMITED_QUIRKS_PUBLIC_IDENTIFIER_PREFIXES,
            )
        }) {
            return if system_identifier.is_some_and(|identifier| !identifier.is_empty()) {
                DoctypeMode::LimitedQuirks
            } else {
                DoctypeMode::Quirks
            };
        }

        DoctypeMode::NoQuirks
    }

    fn new(
        name: String,
        public_identifier: Option<String>,
        system_identifier: Option<String>,
        force_quirks: bool,
    ) -> Self {
        Self {
            name,
            public_identifier,
            system_identifier,
            force_quirks,
        }
    }

    fn malformed() -> Self {
        Self::new(String::new(), None, None, true)
    }
}

fn starts_with_any_ascii_case_insensitive(value: &str, prefixes: &[&str]) -> bool {
    prefixes
        .iter()
        .any(|prefix| starts_with_ascii_case_insensitive(value, prefix))
}

fn starts_with_ascii_case_insensitive(value: &str, prefix: &str) -> bool {
    value
        .as_bytes()
        .get(..prefix.len())
        .is_some_and(|candidate| candidate.eq_ignore_ascii_case(prefix.as_bytes()))
}

fn read_identifier(source: &str, cursor: &mut usize) -> Option<(String, bool)> {
    let bytes = source.as_bytes();
    let quote = *bytes.get(*cursor)?;
    if !matches!(quote, b'\'' | b'"') {
        return None;
    }

    *cursor += 1;
    let start = *cursor;
    while let Some(&byte) = bytes.get(*cursor) {
        if byte == quote {
            let value = source[start..*cursor].to_owned();
            *cursor += 1;
            return Some((value, true));
        }
        *cursor += 1;
    }

    Some((source[start..].to_owned(), false))
}

fn skip_whitespace(bytes: &[u8], cursor: &mut usize) {
    while bytes.get(*cursor).is_some_and(|byte| is_whitespace(*byte)) {
        *cursor += 1;
    }
}

fn is_whitespace(byte: u8) -> bool {
    matches!(byte, b'\t' | b'\n' | b'\x0C' | b'\r' | b' ')
}

#[cfg(test)]
mod tests {
    use super::{DoctypeInfo, DoctypeMode};

    #[test]
    fn rejects_raw_values_without_the_parser_prefix_as_quirks() {
        let info = DoctypeInfo::from_raw("html");

        assert!(info.name.is_empty());
        assert!(info.force_quirks);
        assert_eq!(info.compatibility_mode(), DoctypeMode::Quirks);
    }
}

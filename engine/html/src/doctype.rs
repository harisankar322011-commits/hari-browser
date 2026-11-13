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

            let (system_identifier, terminated) =
                read_identifier(after_keyword, &mut cursor).expect("quote was checked above");
            skip_whitespace(bytes, &mut cursor);
            let force_quirks = !terminated || cursor != bytes.len();
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
                !terminated || cursor != bytes.len(),
            );
        }

        Self::new(name, None, None, true)
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
    use super::DoctypeInfo;

    #[test]
    fn rejects_raw_values_without_the_parser_prefix_as_quirks() {
        let info = DoctypeInfo::from_raw("html");

        assert!(info.name.is_empty());
        assert!(info.force_quirks);
    }
}

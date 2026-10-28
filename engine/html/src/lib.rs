//! A deliberately small, bounded HTML parser for the experimental HARI Engine.
//!
//! This is not an HTML Living Standard implementation. It does not run scripts
//! or fetch subresources. It decodes numeric references plus a deliberately small
//! named-reference subset, but does not include the full HTML entity catalogue.

use hari_engine_core::{EngineLimits, InputError, validate_document_input};

const HARD_MAX_DOCUMENT_BYTES: usize = 1_048_576;
const HARD_MAX_NODES: usize = 100_000;
const HARD_MAX_DEPTH: usize = 256;
const HARD_MAX_ATTRIBUTES_PER_ELEMENT: usize = 256;

/// Hard limits applied before and during parsing.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct HtmlLimits {
    pub max_document_bytes: usize,
    pub max_nodes: usize,
    pub max_depth: usize,
    pub max_attributes_per_element: usize,
}

impl Default for HtmlLimits {
    fn default() -> Self {
        Self {
            max_document_bytes: HARD_MAX_DOCUMENT_BYTES,
            max_nodes: HARD_MAX_NODES,
            max_depth: HARD_MAX_DEPTH,
            max_attributes_per_element: HARD_MAX_ATTRIBUTES_PER_ELEMENT,
        }
    }
}

/// A bounded parser failure.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ParseError {
    Input(InputError),
    NodeLimitExceeded,
    DepthLimitExceeded,
    AttributeLimitExceeded,
}

/// Parsed node kind. Element names and attribute names are normalized to ASCII lowercase.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum NodeKind {
    Document,
    Doctype(String),
    Comment(String),
    Element { tag_name: String },
    Text(String),
}

/// One HTML attribute. A boolean attribute has an empty value.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Attribute {
    pub name: String,
    pub value: String,
}

/// A simple owned tree node. This is an internal HARI representation, not a standards DOM.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Node {
    pub kind: NodeKind,
    pub attributes: Vec<Attribute>,
    pub children: Vec<Node>,
}

impl Node {
    /// Returns the normalized element tag name, if this is an element.
    pub fn tag_name(&self) -> Option<&str> {
        match &self.kind {
            NodeKind::Element { tag_name } => Some(tag_name),
            _ => None,
        }
    }

    /// Returns the first attribute with this name (ASCII case-insensitive).
    pub fn attribute(&self, name: &str) -> Option<&str> {
        self.attributes
            .iter()
            .find(|attribute| attribute.name.eq_ignore_ascii_case(name))
            .map(|attribute| attribute.value.as_str())
    }

    /// Concatenates descendant text nodes in tree order.
    pub fn text_content(&self) -> String {
        fn append_text(node: &Node, output: &mut String) {
            if let NodeKind::Text(value) = &node.kind {
                output.push_str(value);
            }
            for child in &node.children {
                append_text(child, output);
            }
        }

        let mut output = String::new();
        append_text(self, &mut output);
        output
    }
}

#[derive(Debug)]
struct TempNode {
    kind: NodeKind,
    attributes: Vec<Attribute>,
    children: Vec<usize>,
}

impl TempNode {
    fn new(kind: NodeKind, attributes: Vec<Attribute>) -> Self {
        Self {
            kind,
            attributes,
            children: Vec::new(),
        }
    }
}

/// Parses a UTF-8 HTML byte sequence into a simple tree.
///
/// This parser is intentionally conservative in scope: it supports element nesting,
/// end-tag unwinding, comments, doctypes, quoted/unquoted/boolean attributes, void
/// elements, script/style raw text, title/textarea RCDATA, and a limited character
/// reference subset. It performs no JavaScript execution or networking. Callers must
/// not treat successful parsing as a content-safety verdict.
pub fn parse_document(bytes: &[u8], limits: &HtmlLimits) -> Result<Node, ParseError> {
    // Callers may tighten these limits but cannot raise the absolute safety ceilings.
    let effective_limits = HtmlLimits {
        max_document_bytes: limits.max_document_bytes.min(HARD_MAX_DOCUMENT_BYTES),
        max_nodes: limits.max_nodes.min(HARD_MAX_NODES),
        max_depth: limits.max_depth.min(HARD_MAX_DEPTH),
        max_attributes_per_element: limits
            .max_attributes_per_element
            .min(HARD_MAX_ATTRIBUTES_PER_ELEMENT),
    };

    let source = validate_document_input(
        bytes,
        &EngineLimits {
            max_document_bytes: effective_limits.max_document_bytes,
        },
    )
    .map_err(ParseError::Input)?;

    let mut parser = Parser::new(source, &effective_limits);
    parser.parse()?;
    Ok(materialize(0, &parser.nodes))
}

struct Parser<'a> {
    source: &'a str,
    bytes: &'a [u8],
    position: usize,
    limits: &'a HtmlLimits,
    nodes: Vec<TempNode>,
    open_elements: Vec<usize>,
}

impl<'a> Parser<'a> {
    fn new(source: &'a str, limits: &'a HtmlLimits) -> Self {
        Self {
            source,
            bytes: source.as_bytes(),
            position: 0,
            limits,
            nodes: vec![TempNode::new(NodeKind::Document, Vec::new())],
            open_elements: vec![0],
        }
    }

    fn parse(&mut self) -> Result<(), ParseError> {
        while self.position < self.bytes.len() {
            if let Some(tag_name) = self.current_raw_text_element() {
                self.consume_raw_text(tag_name)?;
                continue;
            }

            if self.starts_comment(self.position) {
                self.parse_comment()?;
            } else if self.starts_doctype(self.position) {
                self.parse_doctype()?;
            } else if self.is_end_tag_start(self.position) {
                self.parse_end_tag();
            } else if self.is_start_tag_start(self.position) {
                self.parse_start_tag()?;
            } else {
                self.parse_text()?;
            }
        }
        Ok(())
    }

    fn current_raw_text_element(&self) -> Option<&'static str> {
        let index = *self.open_elements.last()?;
        match &self.nodes[index].kind {
            NodeKind::Element { tag_name } => match tag_name.as_str() {
                "script" => Some("script"),
                "style" => Some("style"),
                "textarea" => Some("textarea"),
                "title" => Some("title"),
                _ => None,
            },
            _ => None,
        }
    }

    fn consume_raw_text(&mut self, tag_name: &str) -> Result<(), ParseError> {
        if let Some(end) = self.find_raw_closing(self.position, tag_name) {
            if end > self.position {
                let raw = &self.source[self.position..end];
                let value = decode_raw_text_content(tag_name, raw);
                self.position = end;
                self.add_node(NodeKind::Text(value), Vec::new())?;
            } else {
                // A closing tag at the current position must be handled now,
                // otherwise an empty raw-text element would loop forever.
                self.parse_end_tag();
                return Ok(());
            }
        } else {
            let raw = &self.source[self.position..];
            let value = decode_raw_text_content(tag_name, raw);
            self.position = self.bytes.len();
            if !value.is_empty() {
                self.add_node(NodeKind::Text(value), Vec::new())?;
            }
        }
        Ok(())
    }

    fn find_raw_closing(&self, from: usize, tag_name: &str) -> Option<usize> {
        let mut index = from;
        while index + 2 + tag_name.len() <= self.bytes.len() {
            if self.bytes[index] == b'<'
                && self.bytes[index + 1] == b'/'
                && self.bytes[index + 2..index + 2 + tag_name.len()]
                    .eq_ignore_ascii_case(tag_name.as_bytes())
            {
                let boundary = self.bytes.get(index + 2 + tag_name.len()).copied();
                if boundary
                    .is_none_or(|byte| byte.is_ascii_whitespace() || byte == b'>' || byte == b'/')
                {
                    return Some(index);
                }
            }
            index += 1;
        }
        None
    }

    fn parse_comment(&mut self) -> Result<(), ParseError> {
        let start = self.position + 4;
        let content_end = find_bytes(self.bytes, start, b"-->").unwrap_or(self.bytes.len());
        let value = self.source[start..content_end].to_owned();
        if content_end == self.bytes.len() {
            self.position = self.bytes.len();
        } else {
            self.position = content_end + 3;
        }
        self.add_node(NodeKind::Comment(value), Vec::new())?;
        Ok(())
    }

    fn parse_doctype(&mut self) -> Result<(), ParseError> {
        let start = self.position + 2;
        let end = find_byte(self.bytes, start, b'>').unwrap_or(self.bytes.len());
        let value = self.source[start..end].trim().to_owned();
        self.position = if end < self.bytes.len() { end + 1 } else { end };
        self.add_node(NodeKind::Doctype(value), Vec::new())?;
        Ok(())
    }

    fn parse_end_tag(&mut self) {
        self.position += 2; // </
        while self.position < self.bytes.len() && self.bytes[self.position].is_ascii_whitespace() {
            self.position += 1;
        }
        let name_start = self.position;
        while self.position < self.bytes.len()
            && !self.bytes[self.position].is_ascii_whitespace()
            && self.bytes[self.position] != b'>'
            && self.bytes[self.position] != b'/'
        {
            self.position += 1;
        }
        let tag_name = self.source[name_start..self.position].to_ascii_lowercase();
        while self.position < self.bytes.len() && self.bytes[self.position] != b'>' {
            self.position += 1;
        }
        if self.position < self.bytes.len() {
            self.position += 1;
        }

        if tag_name.is_empty() {
            return;
        }
        let matching_index = self.open_elements.iter().rposition(|&index| {
            matches!(
                &self.nodes[index].kind,
                NodeKind::Element { tag_name: open_name } if *open_name == tag_name
            )
        });
        if let Some(index) = matching_index {
            self.open_elements.truncate(index);
        }
    }

    fn parse_start_tag(&mut self) -> Result<(), ParseError> {
        self.position += 1; // <
        let name_start = self.position;
        while self.position < self.bytes.len()
            && !self.bytes[self.position].is_ascii_whitespace()
            && self.bytes[self.position] != b'/'
            && self.bytes[self.position] != b'>'
        {
            self.position += 1;
        }

        if name_start == self.position {
            return self.parse_text();
        }

        let tag_name = self.source[name_start..self.position].to_ascii_lowercase();
        let mut attributes = Vec::new();
        let mut seen_attributes = 0usize;
        let mut self_closing = false;

        loop {
            while self.position < self.bytes.len()
                && self.bytes[self.position].is_ascii_whitespace()
            {
                self.position += 1;
            }

            if self.position >= self.bytes.len() {
                break;
            }
            if self.bytes[self.position] == b'>' {
                self.position += 1;
                break;
            }
            if self.bytes[self.position] == b'/' && self.bytes.get(self.position + 1) == Some(&b'>')
            {
                self.position += 2;
                self_closing = true;
                break;
            }

            let attribute_start = self.position;
            while self.position < self.bytes.len()
                && !self.bytes[self.position].is_ascii_whitespace()
                && !matches!(self.bytes[self.position], b'=' | b'/' | b'>')
            {
                self.position += 1;
            }
            if attribute_start == self.position {
                self.position += 1;
                continue;
            }

            let name = self.source[attribute_start..self.position].to_ascii_lowercase();
            seen_attributes += 1;
            if seen_attributes > self.limits.max_attributes_per_element {
                return Err(ParseError::AttributeLimitExceeded);
            }

            while self.position < self.bytes.len()
                && self.bytes[self.position].is_ascii_whitespace()
            {
                self.position += 1;
            }

            let mut value = String::new();
            if self.bytes.get(self.position) == Some(&b'=') {
                self.position += 1;
                while self.position < self.bytes.len()
                    && self.bytes[self.position].is_ascii_whitespace()
                {
                    self.position += 1;
                }

                if let Some(quote @ (b'\'' | b'"')) = self.bytes.get(self.position).copied() {
                    self.position += 1;
                    let value_start = self.position;
                    while self.position < self.bytes.len() && self.bytes[self.position] != quote {
                        self.position += 1;
                    }
                    value = decode_character_references(&self.source[value_start..self.position]);
                    if self.position < self.bytes.len() {
                        self.position += 1;
                    }
                } else {
                    let value_start = self.position;
                    while self.position < self.bytes.len()
                        && !self.bytes[self.position].is_ascii_whitespace()
                        && self.bytes[self.position] != b'>'
                    {
                        self.position += 1;
                    }
                    value = decode_character_references(&self.source[value_start..self.position]);
                }
            }

            if !attributes
                .iter()
                .any(|attribute: &Attribute| attribute.name == name)
            {
                attributes.push(Attribute { name, value });
            }
        }

        let is_void = is_void_element(&tag_name);
        let current_depth = self.open_elements.len().saturating_sub(1);
        if current_depth + 1 > self.limits.max_depth {
            return Err(ParseError::DepthLimitExceeded);
        }

        let node_index = self.add_node(
            NodeKind::Element {
                tag_name: tag_name.clone(),
            },
            attributes,
        )?;

        if !is_void && !self_closing {
            self.open_elements.push(node_index);
        }
        Ok(())
    }

    fn parse_text(&mut self) -> Result<(), ParseError> {
        let start = self.position;
        let mut end = start;

        if self.bytes.get(start) == Some(&b'<') {
            end = start + 1;
        }
        while end < self.bytes.len() && !self.is_markup_start(end) {
            end += 1;
        }
        if end == start {
            end = (start + 1).min(self.bytes.len());
        }

        let value = decode_character_references(&self.source[start..end]);
        self.position = end;
        if !value.is_empty() {
            self.add_node(NodeKind::Text(value), Vec::new())?;
        }
        Ok(())
    }

    fn is_markup_start(&self, index: usize) -> bool {
        if self.bytes.get(index) != Some(&b'<') {
            return false;
        }
        self.starts_comment(index)
            || self.starts_doctype(index)
            || self.is_start_tag_start(index)
            || self.is_end_tag_start(index)
    }

    fn starts_comment(&self, index: usize) -> bool {
        self.bytes
            .get(index..index + 4)
            .is_some_and(|bytes| bytes == b"<!--")
    }

    fn starts_doctype(&self, index: usize) -> bool {
        let Some(tail) = self.bytes.get(index..) else {
            return false;
        };
        tail.len() >= 9
            && tail[..9].eq_ignore_ascii_case(b"<!doctype")
            && tail
                .get(9)
                .is_none_or(|byte| byte.is_ascii_whitespace() || *byte == b'>')
    }

    fn is_start_tag_start(&self, index: usize) -> bool {
        self.bytes.get(index) == Some(&b'<')
            && self
                .bytes
                .get(index + 1)
                .is_some_and(|byte| byte.is_ascii_alphabetic())
    }

    fn is_end_tag_start(&self, index: usize) -> bool {
        self.bytes.get(index) == Some(&b'<')
            && self.bytes.get(index + 1) == Some(&b'/')
            && self
                .bytes
                .get(index + 2)
                .is_some_and(|byte| byte.is_ascii_alphabetic())
    }

    fn add_node(
        &mut self,
        kind: NodeKind,
        attributes: Vec<Attribute>,
    ) -> Result<usize, ParseError> {
        if self.nodes.len().saturating_sub(1) >= self.limits.max_nodes {
            return Err(ParseError::NodeLimitExceeded);
        }
        let parent_index = *self.open_elements.last().unwrap_or(&0);
        let node_index = self.nodes.len();
        self.nodes.push(TempNode::new(kind, attributes));
        self.nodes[parent_index].children.push(node_index);
        Ok(node_index)
    }
}

/// Decodes the character references deliberately supported by this prototype.
/// Named references are an explicitly small subset; this is not the complete HTML table.
fn decode_character_references(input: &str) -> String {
    let bytes = input.as_bytes();
    let mut output = String::with_capacity(input.len());
    let mut cursor = 0usize;

    while cursor < bytes.len() {
        if bytes[cursor] != b'&' {
            let character = input[cursor..]
                .chars()
                .next()
                .expect("cursor is always at a UTF-8 boundary");
            output.push(character);
            cursor += character.len_utf8();
            continue;
        }

        if let Some((character, next)) = decode_numeric_reference(bytes, cursor) {
            output.push(character);
            cursor = next;
            continue;
        }

        if let Some((character, next)) = decode_named_reference(input, cursor) {
            output.push(character);
            cursor = next;
            continue;
        }

        output.push('&');
        cursor += 1;
    }

    output
}

/// Returns a decoded numeric reference and the byte position immediately after it.
/// A numeric reference requires at least one digit; the semicolon is optional.
fn decode_numeric_reference(bytes: &[u8], ampersand: usize) -> Option<(char, usize)> {
    if bytes.get(ampersand + 1) != Some(&b'#') {
        return None;
    }

    let mut cursor = ampersand + 2;
    let radix = match bytes.get(cursor).copied() {
        Some(b'x' | b'X') => {
            cursor += 1;
            16u32
        }
        _ => 10u32,
    };
    let first_digit = cursor;
    let mut value = 0u32;
    let mut overflow = false;

    while let Some(&byte) = bytes.get(cursor) {
        let digit = match (radix, byte) {
            (10, b'0'..=b'9') => u32::from(byte - b'0'),
            (16, b'0'..=b'9') => u32::from(byte - b'0'),
            (16, b'a'..=b'f') => u32::from(byte - b'a') + 10,
            (16, b'A'..=b'F') => u32::from(byte - b'A') + 10,
            _ => break,
        };
        match value.checked_mul(radix).and_then(|n| n.checked_add(digit)) {
            Some(parsed) if !overflow => value = parsed,
            _ => overflow = true,
        }
        cursor += 1;
    }

    if cursor == first_digit {
        return None;
    }

    if bytes.get(cursor) == Some(&b';') {
        cursor += 1;
    }

    let code_point = if overflow {
        0xFFFD
    } else {
        normalize_numeric_reference(value)
    };
    Some((char::from_u32(code_point).unwrap_or('\u{FFFD}'), cursor))
}

/// Applies the HTML numeric-reference replacement and Windows-1252 compatibility map.
fn normalize_numeric_reference(value: u32) -> u32 {
    if value == 0 || value > 0x10FFFF || (0xD800..=0xDFFF).contains(&value) {
        return 0xFFFD;
    }

    match value {
        0x80 => 0x20AC,
        0x82 => 0x201A,
        0x83 => 0x0192,
        0x84 => 0x201E,
        0x85 => 0x2026,
        0x86 => 0x2020,
        0x87 => 0x2021,
        0x88 => 0x02C6,
        0x89 => 0x2030,
        0x8A => 0x0160,
        0x8B => 0x2039,
        0x8C => 0x0152,
        0x8E => 0x017D,
        0x91 => 0x2018,
        0x92 => 0x2019,
        0x93 => 0x201C,
        0x94 => 0x201D,
        0x95 => 0x2022,
        0x96 => 0x2013,
        0x97 => 0x2014,
        0x98 => 0x02DC,
        0x99 => 0x2122,
        0x9A => 0x0161,
        0x9B => 0x203A,
        0x9C => 0x0153,
        0x9E => 0x017E,
        0x9F => 0x0178,
        _ => value,
    }
}

/// Recognizes only short, semicolon-terminated, supported named references.
fn decode_named_reference(input: &str, ampersand: usize) -> Option<(char, usize)> {
    let bytes = input.as_bytes();
    let mut end = ampersand + 1;

    while end < bytes.len() && end - ampersand <= 33 && bytes[end].is_ascii_alphanumeric() {
        end += 1;
    }

    if bytes.get(end) != Some(&b';') || end == ampersand + 1 {
        return None;
    }

    let name = &input[ampersand + 1..end];
    Some((named_reference(name)?, end + 1))
}

fn named_reference(name: &str) -> Option<char> {
    Some(match name {
        "amp" | "AMP" => '&',
        "lt" | "LT" => '<',
        "gt" | "GT" => '>',
        "quot" | "QUOT" => '"',
        "apos" => '\'',
        "nbsp" | "NonBreakingSpace" => '\u{00A0}',
        "copy" | "COPY" => '\u{00A9}',
        "reg" | "REG" => '\u{00AE}',
        "trade" => '\u{2122}',
        "euro" => '\u{20AC}',
        "pound" => '\u{00A3}',
        "cent" => '\u{00A2}',
        "yen" => '\u{00A5}',
        "mdash" => '\u{2014}',
        "ndash" => '\u{2013}',
        "hellip" => '\u{2026}',
        "lsquo" => '\u{2018}',
        "rsquo" => '\u{2019}',
        "ldquo" => '\u{201C}',
        "rdquo" => '\u{201D}',
        "bull" => '\u{2022}',
        "middot" => '\u{00B7}',
        "times" => '\u{00D7}',
        "divide" => '\u{00F7}',
        "plusmn" => '\u{00B1}',
        "deg" => '\u{00B0}',
        "para" => '\u{00B6}',
        "sect" => '\u{00A7}',
        _ => return None,
    })
}

/// HTML title and textarea are RCDATA: references decode there but not in script/style.
fn decode_raw_text_content(tag_name: &str, input: &str) -> String {
    if matches!(tag_name, "title" | "textarea") {
        decode_character_references(input)
    } else {
        input.to_owned()
    }
}

fn is_void_element(tag_name: &str) -> bool {
    matches!(
        tag_name,
        "area"
            | "base"
            | "br"
            | "col"
            | "embed"
            | "hr"
            | "img"
            | "input"
            | "link"
            | "meta"
            | "param"
            | "source"
            | "track"
            | "wbr"
    )
}

fn find_byte(bytes: &[u8], from: usize, target: u8) -> Option<usize> {
    bytes
        .get(from..)?
        .iter()
        .position(|byte| *byte == target)
        .map(|offset| from + offset)
}

fn find_bytes(bytes: &[u8], from: usize, target: &[u8]) -> Option<usize> {
    if target.is_empty() {
        return Some(from);
    }
    bytes
        .get(from..)?
        .windows(target.len())
        .position(|window| window == target)
        .map(|offset| from + offset)
}

fn materialize(index: usize, nodes: &[TempNode]) -> Node {
    let temp = &nodes[index];
    Node {
        kind: temp.kind.clone(),
        attributes: temp.attributes.clone(),
        children: temp
            .children
            .iter()
            .map(|child_index| materialize(*child_index, nodes))
            .collect(),
    }
}

#[cfg(test)]
mod tests {
    use super::{Attribute, HtmlLimits, Node, NodeKind, ParseError, parse_document};
    use hari_engine_core::InputError;

    fn element<'a>(node: &'a Node, expected: &str) -> &'a Node {
        match &node.kind {
            NodeKind::Element { tag_name } if tag_name == expected => node,
            other => panic!("expected element {expected}, got {other:?}"),
        }
    }

    fn text(node: &Node) -> &str {
        match &node.kind {
            NodeKind::Text(value) => value,
            other => panic!("expected text, got {other:?}"),
        }
    }

    #[test]
    fn parses_basic_document_into_nested_nodes() {
        let document = parse_document(
            b"<!doctype html><html><head><title>HARI</title></head><body><h1>Hello</h1><p>World</p></body></html>",
            &HtmlLimits::default(),
        )
        .unwrap();

        assert!(matches!(
            document.children[0].kind,
            NodeKind::Doctype(ref value) if value.eq_ignore_ascii_case("doctype html")
        ));
        let html = element(&document.children[1], "html");
        let head = element(&html.children[0], "head");
        let title = element(&head.children[0], "title");
        assert_eq!(text(&title.children[0]), "HARI");
        let body = element(&html.children[1], "body");
        assert_eq!(text(&element(&body.children[0], "h1").children[0]), "Hello");
        assert_eq!(text(&element(&body.children[1], "p").children[0]), "World");
    }

    #[test]
    fn parses_quoted_unquoted_and_boolean_attributes() {
        let document = parse_document(
            b"<img alt='tiny cat' src=/cat.png disabled><br/>after",
            &HtmlLimits::default(),
        )
        .unwrap();

        let image = element(&document.children[0], "img");
        assert_eq!(
            image.attributes,
            vec![
                Attribute {
                    name: "alt".into(),
                    value: "tiny cat".into()
                },
                Attribute {
                    name: "src".into(),
                    value: "/cat.png".into()
                },
                Attribute {
                    name: "disabled".into(),
                    value: String::new()
                },
            ]
        );
        assert!(element(&document.children[1], "br").children.is_empty());
        assert_eq!(text(&document.children[2]), "after");
    }

    #[test]
    fn ignores_self_closing_syntax_for_non_void_html_elements() {
        let document = parse_document(
            b"<div/>inside<span>nested</span></div><p>after</p>",
            &HtmlLimits::default(),
        )
        .unwrap();

        let div = element(&document.children[0], "div");
        assert_eq!(div.text_content(), "insidenested");
        assert_eq!(element(&document.children[1], "p").text_content(), "after");
    }

    #[test]
    fn closing_tag_unwinds_to_matching_open_element() {
        let document =
            parse_document(b"<div><p>one</div><span>two", &HtmlLimits::default()).unwrap();

        let div = element(&document.children[0], "div");
        assert_eq!(text(&element(&div.children[0], "p").children[0]), "one");
        assert_eq!(
            text(&element(&document.children[1], "span").children[0]),
            "two"
        );
    }

    #[test]
    fn preserves_comments_and_doctype_as_distinct_nodes() {
        let document = parse_document(
            b"<!doctype html><!--safe note--><p>x</p>",
            &HtmlLimits::default(),
        )
        .unwrap();

        assert!(matches!(document.children[0].kind, NodeKind::Doctype(_)));
        assert!(matches!(
            document.children[1].kind,
            NodeKind::Comment(ref value) if value == "safe note"
        ));
    }

    #[test]
    fn raw_text_elements_keep_less_than_signs_as_text() {
        let document = parse_document(
            b"<script>if (a < b) { c(); }</script><p>done</p>",
            &HtmlLimits::default(),
        )
        .unwrap();

        let script = element(&document.children[0], "script");
        assert_eq!(text(&script.children[0]), "if (a < b) { c(); }");
        assert_eq!(element(&document.children[1], "p").children.len(), 1);
    }

    #[test]
    fn handles_empty_raw_text_elements_without_stalling() {
        let document =
            parse_document(b"<script></script><p>done</p>", &HtmlLimits::default()).unwrap();

        assert!(element(&document.children[0], "script").children.is_empty());
        assert_eq!(
            text(&element(&document.children[1], "p").children[0]),
            "done"
        );
    }

    #[test]
    fn rejects_input_over_byte_limit() {
        let limits = HtmlLimits {
            max_document_bytes: 3,
            ..HtmlLimits::default()
        };
        assert_eq!(
            parse_document(b"<p>x</p>", &limits),
            Err(ParseError::Input(InputError::DocumentTooLarge))
        );
    }

    #[test]
    fn rejects_invalid_utf8_and_nul() {
        assert_eq!(
            parse_document(&[0xff], &HtmlLimits::default()),
            Err(ParseError::Input(InputError::InvalidUtf8))
        );
        assert_eq!(
            parse_document(b"<p>\0</p>", &HtmlLimits::default()),
            Err(ParseError::Input(InputError::ForbiddenControlCharacter))
        );
    }

    #[test]
    fn refuses_more_nodes_than_configured() {
        let limits = HtmlLimits {
            max_nodes: 1,
            ..HtmlLimits::default()
        };
        assert_eq!(
            parse_document(b"<p>text</p>", &limits),
            Err(ParseError::NodeLimitExceeded)
        );
    }

    #[test]
    fn refuses_element_nesting_beyond_limit() {
        let limits = HtmlLimits {
            max_depth: 2,
            ..HtmlLimits::default()
        };
        assert_eq!(
            parse_document(b"<a><b><c>x</c></b></a>", &limits),
            Err(ParseError::DepthLimitExceeded)
        );
    }

    #[test]
    fn refuses_too_many_attributes() {
        let limits = HtmlLimits {
            max_attributes_per_element: 1,
            ..HtmlLimits::default()
        };
        assert_eq!(
            parse_document(b"<p a=1 b=2>x</p>", &limits),
            Err(ParseError::AttributeLimitExceeded)
        );
    }

    #[test]
    fn treats_lone_less_than_as_text_without_panicking() {
        let document = parse_document(b"1 < 2", &HtmlLimits::default()).unwrap();
        assert_eq!(text(&document.children[0]), "1 < 2");
    }

    #[test]
    fn preserves_unicode_text_and_attribute_values() {
        let source = "<p title=\"café 😀\">മലയാളം 中文</p>";
        let document = parse_document(source.as_bytes(), &HtmlLimits::default()).unwrap();
        let paragraph = element(&document.children[0], "p");

        assert_eq!(paragraph.attribute("title"), Some("café 😀"));
        assert_eq!(paragraph.text_content(), "മലയാളം 中文");
    }

    #[test]
    fn decodes_numeric_references_in_text_and_attributes() {
        let source = r#"<p title="A &#x1F600; &#169;" data-value=v&#97;lue>&#65;&#x1F642; &#0; &#xD800; &#x110000; &#128;</p>"#;
        let document = parse_document(source.as_bytes(), &HtmlLimits::default()).unwrap();
        let paragraph = element(&document.children[0], "p");

        assert_eq!(paragraph.attribute("title"), Some("A 😀 ©"));
        assert_eq!(paragraph.attribute("data-value"), Some("value"));
        assert_eq!(paragraph.text_content(), "A🙂 � � � €");
    }

    #[test]
    fn decodes_supported_named_references_once() {
        let document = parse_document(
            b"<p>&amp; &lt; &gt; &quot; &apos; &nbsp; &copy; &AMP;</p>",
            &HtmlLimits::default(),
        )
        .unwrap();

        assert_eq!(
            document.children[0].text_content(),
            "& < > \" ' \u{00A0} © &"
        );
    }

    #[test]
    fn decodes_references_in_rcdata_but_preserves_raw_text_elements() {
        let document = parse_document(
            b"<title>Fish &amp; Chips</title><textarea>&lt;b&gt;hi&lt;/b&gt;</textarea><script>if (a &amp;&amp; b < c)</script><style>p::before { content: '&amp;'; }</style>",
            &HtmlLimits::default(),
        )
        .unwrap();

        assert_eq!(
            element(&document.children[0], "title").text_content(),
            "Fish & Chips"
        );
        assert_eq!(
            element(&document.children[1], "textarea").text_content(),
            "<b>hi</b>"
        );
        assert_eq!(
            element(&document.children[2], "script").text_content(),
            "if (a &amp;&amp; b < c)"
        );
        assert_eq!(
            element(&document.children[3], "style").text_content(),
            "p::before { content: '&amp;'; }"
        );
    }

    #[test]
    fn preserves_unknown_or_malformed_references_and_decodes_no_output_recursively() {
        let document = parse_document(
            b"<p>&unknown; &amp & #65; &#x; &#; &amp;lt;</p>",
            &HtmlLimits::default(),
        )
        .unwrap();

        assert_eq!(
            document.children[0].text_content(),
            "&unknown; &amp & #65; &#x; &#; &lt;"
        );
    }

    #[test]
    fn malformed_markup_corpus_is_handled_without_panicking() {
        let samples = [
            "<div",
            "<p title='unterminated",
            "<!-- unterminated comment",
            "<!doctype html",
            "<script>if (left < right) {",
            "<div><p>before</div></p>",
            "<textarea>unclosed <b>looks like text",
            "<p a=one a=two>duplicate attribute</p>",
        ];

        for source in samples {
            assert!(
                parse_document(source.as_bytes(), &HtmlLimits::default()).is_ok(),
                "the bounded parser should recover from this malformed input: {source:?}"
            );
        }
    }

    #[test]
    fn configured_depth_cannot_disable_hard_nesting_ceiling() {
        let source = format!("{}{}", "<div>".repeat(257), "</div>".repeat(257));
        let limits = HtmlLimits {
            max_depth: usize::MAX,
            ..HtmlLimits::default()
        };
        assert_eq!(
            parse_document(source.as_bytes(), &limits),
            Err(ParseError::DepthLimitExceeded)
        );
    }

    #[test]
    fn configured_node_budget_cannot_disable_hard_node_ceiling() {
        let source = "<br/>".repeat(100_001);
        let limits = HtmlLimits {
            max_nodes: usize::MAX,
            ..HtmlLimits::default()
        };
        assert_eq!(
            parse_document(source.as_bytes(), &limits),
            Err(ParseError::NodeLimitExceeded)
        );
    }

    #[test]
    fn configured_attribute_budget_cannot_disable_hard_attribute_ceiling() {
        let attributes = (0..257)
            .map(|index| format!(" a{index}='x'"))
            .collect::<String>();
        let source = format!("<p{attributes}></p>");
        let limits = HtmlLimits {
            max_attributes_per_element: usize::MAX,
            ..HtmlLimits::default()
        };
        assert_eq!(
            parse_document(source.as_bytes(), &limits),
            Err(ParseError::AttributeLimitExceeded)
        );
    }

    #[test]
    fn configured_byte_budget_cannot_disable_hard_document_ceiling() {
        let source = vec![b'a'; 1_048_577];
        let limits = HtmlLimits {
            max_document_bytes: usize::MAX,
            ..HtmlLimits::default()
        };
        assert_eq!(
            parse_document(&source, &limits),
            Err(ParseError::Input(InputError::DocumentTooLarge))
        );
    }
}

//! A deliberately small, bounded HTML parser for the experimental HARI Engine.
//!
//! This is not an HTML Living Standard implementation. It does not run scripts,
//! fetch subresources, decode character references, or implement browser layout.
//! It builds a simple tree so later DOM/layout work can begin behind tests.

use hari_engine_core::{validate_document_input, EngineLimits, InputError};

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
            max_document_bytes: 1_048_576,
            max_nodes: 100_000,
            max_depth: 256,
            max_attributes_per_element: 256,
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
    fn new(kind: NodeKind, attributes: Vec<Attribute>) -> Self {
        Self {
            kind,
            attributes,
            children: Vec::new(),
        }
    }

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
/// elements, and raw-text handling for script/style/textarea/title. It performs no
/// JavaScript execution or networking. Callers must not treat successful parsing as
/// a content-safety verdict.
pub fn parse_document(bytes: &[u8], limits: &HtmlLimits) -> Result<Node, ParseError> {
    let source = validate_document_input(
        bytes,
        &EngineLimits {
            max_document_bytes: limits.max_document_bytes,
        },
    )
    .map_err(ParseError::Input)?;

    let mut parser = Parser::new(source, limits);
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

    fn current_raw_text_element(&self) -> Option<&str> {
        let index = *self.open_elements.last()?;
        match &self.nodes[index].kind {
            NodeKind::Element { tag_name }
                if matches!(tag_name.as_str(), "script" | "style" | "textarea" | "title") =>
            {
                Some(tag_name)
            }
            _ => None,
        }
    }

    fn consume_raw_text(&mut self, tag_name: &str) -> Result<(), ParseError> {
        if let Some(end) = self.find_raw_closing(self.position, tag_name) {
            if end > self.position {
                let value = self.source[self.position..end].to_owned();
                self.position = end;
                self.add_node(NodeKind::Text(value), Vec::new())?;
            } else {
                // The next loop iteration will consume the closing tag.
                self.position = end;
            }
        } else {
            let value = self.source[self.position..].to_owned();
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
                if boundary.is_none_or(|byte| byte.is_ascii_whitespace() || byte == b'>' || byte == b'/') {
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
            if self.bytes[self.position] == b'/'
                && self.bytes.get(self.position + 1) == Some(&b'>')
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
                    value = self.source[value_start..self.position].to_owned();
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
                    value = self.source[value_start..self.position].to_owned();
                }
            }

            if !attributes.iter().any(|attribute: &Attribute| attribute.name == name) {
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

        let value = self.source[start..end].to_owned();
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
        self.bytes.get(index..index + 4) == Some(b"<!--")
    }

    fn starts_doctype(&self, index: usize) -> bool {
        let Some(tail) = self.bytes.get(index..) else {
            return false;
        };
        tail.len() >= 9
            && tail[..9].eq_ignore_ascii_case(b"<!doctype")
            && tail.get(9).is_none_or(|byte| byte.is_ascii_whitespace() || *byte == b'>')
    }

    fn is_start_tag_start(&self, index: usize) -> bool {
        self.bytes.get(index) == Some(&b'<')
            && self.bytes.get(index + 1).is_some_and(u8::is_ascii_alphabetic)
    }

    fn is_end_tag_start(&self, index: usize) -> bool {
        self.bytes.get(index) == Some(&b'<')
            && self.bytes.get(index + 1) == Some(&b'/')
            && self.bytes.get(index + 2).is_some_and(u8::is_ascii_alphabetic)
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
    bytes.get(from..)?.iter().position(|byte| *byte == target).map(|offset| from + offset)
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
    use super::{parse_document, Attribute, HtmlLimits, Node, NodeKind, ParseError};
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
                Attribute { name: "alt".into(), value: "tiny cat".into() },
                Attribute { name: "src".into(), value: "/cat.png".into() },
                Attribute { name: "disabled".into(), value: String::new() },
            ]
        );
        assert!(element(&document.children[1], "br").children.is_empty());
        assert_eq!(text(&document.children[2]), "after");
    }

    #[test]
    fn closing_tag_unwinds_to_matching_open_element() {
        let document = parse_document(b"<div><p>one</div><span>two", &HtmlLimits::default())
            .unwrap();

        let div = element(&document.children[0], "div");
        assert_eq!(text(&element(&div.children[0], "p").children[0]), "one");
        assert_eq!(text(&element(&document.children[1], "span").children[0]), "two");
    }

    #[test]
    fn preserves_comments_and_doctype_as_distinct_nodes() {
        let document =
            parse_document(b"<!doctype html><!--safe note--><p>x</p>", &HtmlLimits::default())
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
    fn rejects_input_over_byte_limit() {
        let limits = HtmlLimits { max_document_bytes: 3, ..HtmlLimits::default() };
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
        let limits = HtmlLimits { max_nodes: 1, ..HtmlLimits::default() };
        assert_eq!(
            parse_document(b"<p>text</p>", &limits),
            Err(ParseError::NodeLimitExceeded)
        );
    }

    #[test]
    fn refuses_element_nesting_beyond_limit() {
        let limits = HtmlLimits { max_depth: 2, ..HtmlLimits::default() };
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
}

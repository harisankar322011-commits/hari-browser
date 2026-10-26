//! HARI HTML parser tests are written first; implementation follows in the next commit.

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

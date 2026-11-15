use hari_engine_html::{
    DoctypeMode, HtmlLimits, NodeKind, parse_document, parse_document_with_mode,
};

fn parse_with_mode(input: &str) -> hari_engine_html::ParsedDocument {
    parse_document_with_mode(input.as_bytes(), &HtmlLimits::default()).unwrap()
}

fn mode(input: &str) -> DoctypeMode {
    parse_with_mode(input).mode
}

#[test]
fn modern_doctype_selects_no_quirks_mode_before_document_elements() {
    let parsed = parse_with_mode("<!doctype html><html><head></head><body>HARI</body></html>");

    assert_eq!(parsed.mode, DoctypeMode::NoQuirks);
    assert!(matches!(&parsed.root.kind, NodeKind::Document));
    assert!(matches!(
        parsed.root.children.first().map(|node| &node.kind),
        Some(NodeKind::Doctype(_))
    ));
}

#[test]
fn legacy_xhtml_doctype_selects_limited_quirks_mode() {
    assert_eq!(
        mode("<!DOCTYPE html PUBLIC '-//W3C//DTD XHTML 1.0 Transitional//EN'><html></html>"),
        DoctypeMode::LimitedQuirks
    );
}

#[test]
fn legacy_html_401_doctype_without_system_identifier_selects_quirks_mode() {
    assert_eq!(
        mode("<!DOCTYPE html PUBLIC '-//W3C//DTD HTML 4.01 Transitional//EN'>"),
        DoctypeMode::Quirks
    );
}

#[test]
fn comments_and_ascii_whitespace_before_doctype_preserve_initial_mode() {
    let parsed = parse_with_mode(" \t\n<!--before-->\n<!DOCTYPE html><p>after</p>");

    assert_eq!(parsed.mode, DoctypeMode::NoQuirks);
    assert_eq!(parsed.root.children.len(), 3);
    assert!(matches!(
        &parsed.root.children[0].kind,
        NodeKind::Comment(_)
    ));
    assert!(matches!(
        &parsed.root.children[1].kind,
        NodeKind::Doctype(_)
    ));
    assert!(matches!(
        &parsed.root.children[2].kind,
        NodeKind::Element { tag_name } if tag_name == "p"
    ));
}

#[test]
fn character_reference_whitespace_before_doctype_is_ignored_in_initial_mode() {
    let parsed = parse_with_mode("&#32;<!DOCTYPE html>");

    assert_eq!(parsed.mode, DoctypeMode::NoQuirks);
    assert_eq!(parsed.root.children.len(), 1);
    assert!(matches!(
        &parsed.root.children[0].kind,
        NodeKind::Doctype(_)
    ));
}

#[test]
fn missing_doctype_defaults_to_quirks_mode_at_eof() {
    for input in ["", " \t\n", "<!--only a comment-->", "<!--comment--> \n"] {
        assert_eq!(mode(input), DoctypeMode::Quirks, "input: {input:?}");
    }
}

#[test]
fn non_whitespace_text_before_doctype_sets_quirks_and_ignores_late_doctype() {
    let parsed = parse_with_mode("text<!DOCTYPE html><p>after</p>");

    assert_eq!(parsed.mode, DoctypeMode::Quirks);
    assert_eq!(parsed.root.children.len(), 2);
    assert!(matches!(
        &parsed.root.children[0].kind,
        NodeKind::Text(value) if value == "text"
    ));
    assert!(matches!(
        &parsed.root.children[1].kind,
        NodeKind::Element { tag_name } if tag_name == "p"
    ));
}

#[test]
fn start_tag_before_doctype_sets_quirks_and_ignores_late_doctype() {
    let parsed = parse_with_mode("<html><!DOCTYPE html><p>after</p>");

    assert_eq!(parsed.mode, DoctypeMode::Quirks);
    assert_eq!(parsed.root.children.len(), 1);
    assert!(matches!(
        &parsed.root.children[0].kind,
        NodeKind::Element { tag_name } if tag_name == "html"
    ));
    assert!(
        parsed.root.children[0]
            .children
            .iter()
            .all(|node| !matches!(&node.kind, NodeKind::Doctype(_)))
    );
}

#[test]
fn end_tag_before_doctype_sets_quirks_and_ignores_late_doctype() {
    let parsed = parse_with_mode("</html><!DOCTYPE html>");

    assert_eq!(parsed.mode, DoctypeMode::Quirks);
    assert!(parsed.root.children.is_empty());
}

#[test]
fn only_the_first_initial_doctype_is_retained_and_controls_mode() {
    let parsed = parse_with_mode(
        "<!DOCTYPE html PUBLIC '-//W3C//DTD HTML 4.01 Transitional//EN' 'legacy.dtd'>\n<!DOCTYPE html PUBLIC 'HTML'><p>content</p>",
    );

    assert_eq!(parsed.mode, DoctypeMode::LimitedQuirks);
    assert_eq!(
        parsed
            .root
            .children
            .iter()
            .filter(|node| matches!(&node.kind, NodeKind::Doctype(_)))
            .count(),
        1
    );
}

#[test]
fn malformed_initial_doctype_uses_its_force_quirks_classification() {
    assert_eq!(mode("<!DOCTYPE html PUBLIC>"), DoctypeMode::Quirks);
}

#[test]
fn legacy_parse_document_api_still_returns_the_document_root() {
    let root = parse_document(b"<!DOCTYPE html><p>HARI</p>", &HtmlLimits::default()).unwrap();

    assert!(matches!(&root.kind, NodeKind::Document));
    assert!(matches!(
        root.children.first().map(|node| &node.kind),
        Some(NodeKind::Doctype(_))
    ));
}

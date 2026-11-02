//! HTML input-stream preprocessing regressions.

use hari_engine_html::{HtmlLimits, NodeKind, parse_document};

#[test]
fn ignores_a_leading_utf8_bom_before_the_doctype() {
    let source = "\u{FEFF}<!doctype html><p>HARI</p>";
    let document = parse_document(source.as_bytes(), &HtmlLimits::default()).unwrap();

    assert_eq!(document.children.len(), 2);
    assert!(matches!(
        document.children[0].kind,
        NodeKind::Doctype(ref value) if value.eq_ignore_ascii_case("doctype html")
    ));
    assert_eq!(document.children[1].text_content(), "HARI");
}

#[test]
fn preserves_a_bom_that_occurs_after_the_first_input_character() {
    let source = "<p>before\u{FEFF}after</p>";
    let document = parse_document(source.as_bytes(), &HtmlLimits::default()).unwrap();

    assert_eq!(document.children[0].text_content(), "before\u{FEFF}after");
}

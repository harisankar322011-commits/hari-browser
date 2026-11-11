use hari_engine_html::{HtmlLimits, NodeKind, parse_document};

#[test]
fn doctype_without_whitespace_before_name_is_recovered() {
    let document = parse_document(b"<!DOCTYPEhtml>Hello", &HtmlLimits::default()).unwrap();

    assert_eq!(document.children.len(), 2);
    assert!(matches!(
        &document.children[0].kind,
        NodeKind::Doctype(value) if value == "DOCTYPE html"
    ));
    assert!(matches!(
        &document.children[1].kind,
        NodeKind::Text(value) if value == "Hello"
    ));
}

#[test]
fn doctype_missing_whitespace_recovery_is_ascii_case_insensitive() {
    let document = parse_document(b"<!dOcTyPeHtMl>tail", &HtmlLimits::default()).unwrap();

    assert_eq!(document.children.len(), 2);
    assert!(matches!(
        &document.children[0].kind,
        NodeKind::Doctype(value) if value.eq_ignore_ascii_case("DOCTYPE html")
    ));
    assert!(matches!(
        &document.children[1].kind,
        NodeKind::Text(value) if value == "tail"
    ));
}

#[test]
fn ordinary_doctype_with_whitespace_keeps_its_existing_representation() {
    let document = parse_document(b"<!DOCTYPE html>tail", &HtmlLimits::default()).unwrap();

    assert_eq!(document.children.len(), 2);
    assert!(matches!(
        &document.children[0].kind,
        NodeKind::Doctype(value) if value == "DOCTYPE html"
    ));
}

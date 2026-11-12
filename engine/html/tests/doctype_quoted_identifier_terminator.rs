use hari_engine_html::{HtmlLimits, NodeKind, parse_document};

#[test]
fn greater_than_inside_double_quoted_public_and_system_identifiers_is_preserved() {
    let document = parse_document(
        br#"<!DOCTYPE html PUBLIC "public>identifier" "system>identifier">tail"#,
        &HtmlLimits::default(),
    )
    .unwrap();

    assert_eq!(document.children.len(), 2);
    assert!(matches!(
        &document.children[0].kind,
        NodeKind::Doctype(value)
            if value == r#"DOCTYPE html PUBLIC "public>identifier" "system>identifier""#
    ));
    assert!(matches!(
        &document.children[1].kind,
        NodeKind::Text(value) if value == "tail"
    ));
}

#[test]
fn greater_than_inside_single_quoted_system_identifier_is_preserved() {
    let document = parse_document(
        b"<!DOCTYPE html SYSTEM 'system>identifier'>tail",
        &HtmlLimits::default(),
    )
    .unwrap();

    assert_eq!(document.children.len(), 2);
    assert!(matches!(
        &document.children[0].kind,
        NodeKind::Doctype(value) if value == "DOCTYPE html SYSTEM 'system>identifier'"
    ));
    assert!(matches!(
        &document.children[1].kind,
        NodeKind::Text(value) if value == "tail"
    ));
}

#[test]
fn quotes_after_an_invalid_doctype_name_sequence_do_not_hide_the_terminator() {
    let document = parse_document(
        b"<!DOCTYPE potato taco \"quoted>tail",
        &HtmlLimits::default(),
    )
    .unwrap();

    assert_eq!(document.children.len(), 2);
    assert!(matches!(
        &document.children[0].kind,
        NodeKind::Doctype(value) if value == "DOCTYPE potato taco \"quoted"
    ));
    assert!(matches!(
        &document.children[1].kind,
        NodeKind::Text(value) if value == "tail"
    ));
}

#[test]
fn unterminated_quoted_system_identifier_consumes_through_eof() {
    let document = parse_document(
        b"<!DOCTYPE html SYSTEM \"unfinished > remains",
        &HtmlLimits::default(),
    )
    .unwrap();

    assert_eq!(document.children.len(), 1);
    assert!(matches!(
        &document.children[0].kind,
        NodeKind::Doctype(value) if value == "DOCTYPE html SYSTEM \"unfinished > remains"
    ));
}

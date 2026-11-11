use hari_engine_html::{HtmlLimits, NodeKind, parse_document};

#[test]
fn processing_instruction_bogus_comment_preserves_the_leading_question_mark() {
    let document = parse_document(b"<?xml version=\"1.0\">Hi", &HtmlLimits::default()).unwrap();

    assert_eq!(document.children.len(), 2);
    assert!(matches!(
        &document.children[0].kind,
        NodeKind::Comment(value) if value == "?xml version=\"1.0\""
    ));
    assert!(matches!(
        &document.children[1].kind,
        NodeKind::Text(value) if value == "Hi"
    ));
}

#[test]
fn unterminated_processing_instruction_bogus_comment_preserves_the_leading_question_mark() {
    let document = parse_document(b"<?xml version", &HtmlLimits::default()).unwrap();

    assert_eq!(document.children.len(), 1);
    assert!(matches!(
        &document.children[0].kind,
        NodeKind::Comment(value) if value == "?xml version"
    ));
}

#[test]
fn markup_declaration_bogus_comment_does_not_gain_a_question_mark() {
    let document = parse_document(b"<!bogus>tail", &HtmlLimits::default()).unwrap();

    assert_eq!(document.children.len(), 2);
    assert!(matches!(
        &document.children[0].kind,
        NodeKind::Comment(value) if value == "bogus"
    ));
    assert!(matches!(
        &document.children[1].kind,
        NodeKind::Text(value) if value == "tail"
    ));
}

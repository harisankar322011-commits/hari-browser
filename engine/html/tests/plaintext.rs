use hari_engine_html::{HtmlLimits, Node, NodeKind, parse_document};

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
fn plaintext_consumes_the_rest_of_the_document_as_literal_text() {
    let document = parse_document(
        b"<p>before</p><PLAINTEXT>one &amp; <b>two</b></plaintext><p>ignored",
        &HtmlLimits::default(),
    )
    .unwrap();

    assert_eq!(document.children.len(), 2);
    assert_eq!(element(&document.children[0], "p").text_content(), "before");

    let plaintext = element(&document.children[1], "plaintext");
    assert_eq!(
        text(&plaintext.children[0]),
        "one &amp; <b>two</b></plaintext><p>ignored"
    );
}

#[test]
fn plaintext_start_tag_closes_an_open_paragraph() {
    let document =
        parse_document(b"<p>intro<plaintext>raw", &HtmlLimits::default()).unwrap();

    assert_eq!(document.children.len(), 2);
    assert_eq!(element(&document.children[0], "p").text_content(), "intro");

    let plaintext = element(&document.children[1], "plaintext");
    assert_eq!(text(&plaintext.children[0]), "raw");
}

#[test]
fn empty_plaintext_element_has_no_synthetic_text_child() {
    let document = parse_document(b"<plaintext>", &HtmlLimits::default()).unwrap();

    let plaintext = element(&document.children[0], "plaintext");
    assert!(plaintext.children.is_empty());
}

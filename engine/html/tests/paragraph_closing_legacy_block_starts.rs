use hari_engine_html::{HtmlLimits, Node, NodeKind, parse_document};

fn element<'a>(node: &'a Node, expected: &str) -> &'a Node {
    match &node.kind {
        NodeKind::Element { tag_name } if tag_name == expected => node,
        other => panic!("expected element {expected}, got {other:?}"),
    }
}

fn paragraph_then_block(source: &[u8], expected_block: &str) {
    let document = parse_document(source, &HtmlLimits::default()).unwrap();

    assert_eq!(
        document.children.len(),
        2,
        "the block start tag must close the preceding paragraph"
    );
    assert_eq!(
        element(&document.children[0], "p").text_content(),
        "foo"
    );

    let block = element(&document.children[1], expected_block);
    assert_eq!(block.text_content(), "barbaz");
    assert_eq!(block.children.len(), 2);
    assert!(matches!(
        &block.children[0].kind,
        NodeKind::Text(value) if value == "bar"
    ));
    assert_eq!(element(&block.children[1], "p").text_content(), "baz");
}

#[test]
fn center_start_tag_closes_an_open_paragraph() {
    paragraph_then_block(b"<p>foo<center>bar<p>baz", "center");
}

#[test]
fn listing_start_tag_closes_an_open_paragraph() {
    paragraph_then_block(b"<p>foo<listing>bar<p>baz", "listing");
}

#[test]
fn summary_start_tag_closes_an_open_paragraph() {
    paragraph_then_block(b"<p>foo<summary>bar<p>baz", "summary");
}

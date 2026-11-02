//! Regression coverage for bounded heading-start recovery.

use hari_engine_html::{HtmlLimits, Node, parse_document};

fn element<'a>(node: &'a Node, name: &str) -> &'a Node {
    node.children
        .iter()
        .find(|child| child.tag_name() == Some(name))
        .expect("expected element")
}

#[test]
fn starting_a_heading_when_another_heading_is_current_closes_the_previous_heading() {
    let document = parse_document(
        b"<h1>one<h2>two<h3>three</h3>",
        &HtmlLimits::default(),
    )
    .unwrap();

    assert_eq!(document.children.len(), 3);
    assert_eq!(element(&document, "h1").text_content(), "one");
    assert_eq!(element(&document, "h2").text_content(), "two");
    assert_eq!(element(&document, "h3").text_content(), "three");
}

#[test]
fn heading_start_does_not_pop_a_heading_behind_an_intervening_element() {
    let document = parse_document(
        b"<h1>outer<div><h2>inner</h2></div></h1>",
        &HtmlLimits::default(),
    )
    .unwrap();

    assert_eq!(document.children.len(), 1);
    let heading = element(&document, "h1");
    assert_eq!(heading.text_content(), "outerinner");
    assert_eq!(element(heading, "div").children[0].tag_name(), Some("h2"));
}

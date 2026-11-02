//! Regression coverage for bounded implied table-section insertion.

use hari_engine_html::{HtmlLimits, Node, parse_document};

fn element<'a>(node: &'a Node, name: &str) -> &'a Node {
    node.children
        .iter()
        .find(|child| child.tag_name() == Some(name))
        .expect("expected element")
}

#[test]
fn direct_table_rows_are_inserted_under_one_implied_tbody() {
    let document = parse_document(
        b"<table><tr><td>one</td></tr><tr><td>two</td></tr></table>",
        &HtmlLimits::default(),
    )
    .unwrap();

    let table = element(&document, "table");
    assert_eq!(table.children.len(), 1);
    let body = element(table, "tbody");
    assert_eq!(body.children.len(), 2);
    assert_eq!(body.children[0].tag_name(), Some("tr"));
    assert_eq!(body.children[0].text_content(), "one");
    assert_eq!(body.children[1].tag_name(), Some("tr"));
    assert_eq!(body.children[1].text_content(), "two");
}

#[test]
fn explicit_tbody_is_not_duplicated() {
    let document = parse_document(
        b"<table><tbody><tr><td>explicit</td></tr></tbody></table>",
        &HtmlLimits::default(),
    )
    .unwrap();

    let table = element(&document, "table");
    assert_eq!(table.children.len(), 1);
    let body = element(table, "tbody");
    assert_eq!(body.children.len(), 1);
    assert_eq!(body.children[0].tag_name(), Some("tr"));
    assert_eq!(body.children[0].text_content(), "explicit");
}

#[test]
fn a_row_outside_a_direct_table_context_does_not_gain_an_implied_tbody() {
    let document = parse_document(
        b"<div><tr><td>outside</td></tr></div>",
        &HtmlLimits::default(),
    )
    .unwrap();

    let container = element(&document, "div");
    assert_eq!(container.children[0].tag_name(), Some("tr"));
    assert_eq!(container.children[0].children[0].tag_name(), Some("td"));
}

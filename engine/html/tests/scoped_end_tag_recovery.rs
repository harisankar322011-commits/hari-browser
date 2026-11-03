//! Regression coverage for scope-aware HTML list-item end-tag recovery.

use hari_engine_html::{HtmlLimits, Node, parse_document};

fn element<'a>(node: &'a Node, name: &str) -> &'a Node {
    node.children
        .iter()
        .find(|child| child.tag_name() == Some(name))
        .expect("expected element")
}

#[test]
fn list_item_end_tag_does_not_cross_a_nested_list_boundary() {
    let document = parse_document(
        b"<ul><li>outer<ul></li><li>inner</li></ul><li>after</li></ul>",
        &HtmlLimits::default(),
    )
    .unwrap();

    let outer_list = element(&document, "ul");
    assert_eq!(outer_list.children.len(), 2);

    let outer_item = element(outer_list, "li");
    let nested_list = element(outer_item, "ul");
    assert_eq!(nested_list.children.len(), 1);
    assert_eq!(element(nested_list, "li").text_content(), "inner");
    assert_eq!(outer_item.text_content(), "outerinner");
    assert_eq!(outer_list.children[1].text_content(), "after");
}

#[test]
fn definition_item_end_tag_does_not_cross_a_scope_boundary() {
    let document = parse_document(
        b"<dl><dt>term<button></dt><span>inside</span></button>tail</dt><dd>definition</dd></dl>",
        &HtmlLimits::default(),
    )
    .unwrap();

    let definition_list = element(&document, "dl");
    assert_eq!(definition_list.children.len(), 2);

    let term = element(definition_list, "dt");
    let button = element(term, "button");
    assert_eq!(element(button, "span").text_content(), "inside");
    assert_eq!(term.text_content(), "terminsidetail");
    assert_eq!(
        element(&definition_list.children[1], "dd").text_content(),
        "definition"
    );
}

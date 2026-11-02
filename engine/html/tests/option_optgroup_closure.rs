//! Regression coverage for bounded option/optgroup start-tag recovery.

use hari_engine_html::{HtmlLimits, Node, parse_document};

fn element<'a>(node: &'a Node, name: &str) -> &'a Node {
    node.children
        .iter()
        .find(|child| child.tag_name() == Some(name))
        .expect("expected element")
}

#[test]
fn starting_an_option_closes_the_current_option() {
    let document = parse_document(
        b"<select><option>one<option>two</select>",
        &HtmlLimits::default(),
    )
    .unwrap();

    let select = element(&document, "select");
    assert_eq!(select.children.len(), 2);
    assert_eq!(select.children[0].tag_name(), Some("option"));
    assert_eq!(select.children[0].text_content(), "one");
    assert_eq!(select.children[1].tag_name(), Some("option"));
    assert_eq!(select.children[1].text_content(), "two");
}

#[test]
fn starting_an_optgroup_closes_a_current_option() {
    let document = parse_document(
        b"<select><optgroup label=first><option>one<optgroup label=second>two</optgroup></select>",
        &HtmlLimits::default(),
    )
    .unwrap();

    let select = element(&document, "select");
    let first_group = element(select, "optgroup");
    assert_eq!(first_group.children.len(), 2);
    assert_eq!(first_group.children[0].tag_name(), Some("option"));
    assert_eq!(first_group.children[0].text_content(), "one");
    assert_eq!(first_group.children[1].tag_name(), Some("optgroup"));
    assert_eq!(first_group.children[1].text_content(), "two");
}

#[test]
fn option_recovery_does_not_pop_past_an_intervening_element() {
    let document = parse_document(
        b"<div><option>outer<div><option>inner</div></option></div>",
        &HtmlLimits::default(),
    )
    .unwrap();

    let outer = element(&document, "div");
    let option = element(outer, "option");
    let inner_div = element(option, "div");
    assert_eq!(element(inner_div, "option").text_content(), "inner");
}

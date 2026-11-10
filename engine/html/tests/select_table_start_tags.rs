//! Regression coverage for the bounded select-mode table-token rule.

use hari_engine_html::{HtmlLimits, Node, NodeKind, parse_document};

fn find_element<'a>(node: &'a Node, tag_name: &str) -> Option<&'a Node> {
    if matches!(
        &node.kind,
        NodeKind::Element { tag_name: actual } if actual == tag_name
    ) {
        return Some(node);
    }

    node.children
        .iter()
        .find_map(|child| find_element(child, tag_name))
}

#[test]
fn table_structural_start_tags_are_ignored_when_select_is_current() {
    for tag_name in [
        "caption", "table", "tbody", "tfoot", "thead", "tr", "td", "th",
    ] {
        let source = format!("<select><{tag_name} data-probe=\"ignored\">tail</select>");
        let document = parse_document(source.as_bytes(), &HtmlLimits::default())
            .expect("valid bounded HTML input");
        let select = find_element(&document, "select").expect("select element");

        assert_eq!(
            select.text_content(),
            "tail",
            "{tag_name} token must be ignored without losing following text"
        );
        assert!(
            find_element(&document, tag_name).is_none(),
            "{tag_name} must not be inserted as a child of select"
        );
    }
}

#[test]
fn table_structural_start_tags_are_not_globally_suppressed() {
    for tag_name in [
        "caption", "table", "tbody", "tfoot", "thead", "tr", "td", "th",
    ] {
        let source = format!("<div><{tag_name}></{tag_name}></div>");
        let document = parse_document(source.as_bytes(), &HtmlLimits::default())
            .expect("valid bounded HTML input");

        assert!(
            find_element(&document, tag_name).is_some(),
            "{tag_name} must retain ordinary behavior outside select"
        );
    }
}

#[test]
fn option_content_remains_available_inside_select() {
    let document = parse_document(
        b"<select><option>one</option><option>two</option></select>",
        &HtmlLimits::default(),
    )
    .expect("valid bounded HTML input");
    let select = find_element(&document, "select").expect("select element");

    assert_eq!(select.text_content(), "onetwo");
    assert_eq!(
        select
            .children
            .iter()
            .filter(|child| matches!(&child.kind, NodeKind::Element { tag_name } if tag_name == "option"))
            .count(),
        2
    );
}

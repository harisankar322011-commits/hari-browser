//! Regression coverage for bounded body-mode table-structure token handling.

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

const IGNORED_OUTSIDE_TABLE: &[&str] = &[
    "caption", "col", "colgroup", "frame", "head", "tbody", "td", "tfoot", "th", "thead", "tr",
];

#[test]
fn table_structure_start_tags_are_ignored_outside_table_context() {
    for tag_name in IGNORED_OUTSIDE_TABLE {
        let source = format!("<div><{tag_name} data-probe=\"ignored\">tail</div>");
        let document = parse_document(source.as_bytes(), &HtmlLimits::default())
            .expect("valid bounded HTML input");
        let div = find_element(&document, "div").expect("div element");

        assert_eq!(
            div.text_content(),
            "tail",
            "{tag_name} token must be ignored without losing following text"
        );
        assert!(
            find_element(div, tag_name).is_none(),
            "{tag_name} must not be inserted outside table context"
        );
    }
}

#[test]
fn seven_pinned_direct_select_cases_are_ignored() {
    for tag_name in ["caption", "tbody", "tfoot", "thead", "tr", "td", "th"] {
        let source = format!("<select><{tag_name}>tail</select>");
        let document = parse_document(source.as_bytes(), &HtmlLimits::default())
            .expect("valid bounded HTML input");
        let select = find_element(&document, "select").expect("select element");

        assert_eq!(select.text_content(), "tail", "{tag_name}");
        assert!(
            find_element(select, tag_name).is_none(),
            "{tag_name} must be ignored inside direct select content"
        );
    }
}

#[test]
fn table_structure_tokens_remain_active_inside_table_context() {
    let document = parse_document(
        b"<table><tr><td>cell</td></tr></table>",
        &HtmlLimits::default(),
    )
    .expect("valid bounded HTML input");
    let table = find_element(&document, "table").expect("table element");

    assert!(find_element(table, "tbody").is_some());
    assert!(find_element(table, "tr").is_some());
    assert!(find_element(table, "td").is_some());
    assert_eq!(table.text_content(), "cell");
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

//! Regression coverage for conservative HTML table-row and cell recovery.

use hari_engine_html::{HtmlLimits, Node, parse_document};

fn element<'a>(node: &'a Node, name: &str) -> &'a Node {
    node.children
        .iter()
        .find(|child| child.tag_name() == Some(name))
        .expect("expected element")
}

#[test]
fn cells_started_directly_in_a_table_section_share_one_implied_row() {
    let document = parse_document(
        b"<table><tbody><td>one</td><th>two</th></tbody></table>",
        &HtmlLimits::default(),
    )
    .unwrap();

    let table = element(&document, "table");
    let body = element(table, "tbody");
    assert_eq!(body.children.len(), 1);

    let row = element(body, "tr");
    assert_eq!(row.children.len(), 2);
    assert_eq!(row.children[0].tag_name(), Some("td"));
    assert_eq!(row.children[0].text_content(), "one");
    assert_eq!(row.children[1].tag_name(), Some("th"));
    assert_eq!(row.children[1].text_content(), "two");
}

#[test]
fn a_new_cell_closes_only_the_current_cell_in_its_row() {
    let document = parse_document(
        b"<table><tbody><tr><td>one<th>two</th></tr></tbody></table>",
        &HtmlLimits::default(),
    )
    .unwrap();

    let table = element(&document, "table");
    let body = element(table, "tbody");
    let row = element(body, "tr");

    assert_eq!(row.children.len(), 2);
    assert_eq!(row.children[0].tag_name(), Some("td"));
    assert_eq!(row.children[0].text_content(), "one");
    assert_eq!(row.children[1].tag_name(), Some("th"));
    assert_eq!(row.children[1].text_content(), "two");
}

#[test]
fn a_new_row_closes_the_current_cell_and_row() {
    let document = parse_document(
        b"<table><tbody><tr><td>one<tr><td>two</td></tr></tbody></table>",
        &HtmlLimits::default(),
    )
    .unwrap();

    let table = element(&document, "table");
    let body = element(table, "tbody");
    assert_eq!(body.children.len(), 2);
    assert_eq!(body.children[0].tag_name(), Some("tr"));
    assert_eq!(body.children[0].text_content(), "one");
    assert_eq!(body.children[1].tag_name(), Some("tr"));
    assert_eq!(body.children[1].text_content(), "two");
}

#[test]
fn cell_recovery_does_not_pop_through_an_intervening_nested_element() {
    let document = parse_document(
        b"<table><tbody><tr><td><span>kept<th>nested</th></span></td></tr></tbody></table>",
        &HtmlLimits::default(),
    )
    .unwrap();

    let table = element(&document, "table");
    let body = element(table, "tbody");
    let row = element(body, "tr");
    let cell = element(row, "td");
    let span = element(cell, "span");

    assert_eq!(span.children.len(), 2);
    assert_eq!(span.children[0].tag_name(), None);
    assert_eq!(span.children[1].tag_name(), Some("th"));
    assert_eq!(span.text_content(), "keptnested");
}

#[test]
fn nested_table_sections_get_their_own_implied_rows() {
    let document = parse_document(
        b"<table><tbody><tr><td>outer<table><tbody><td>inner</tbody></table>tail</td></tr></tbody></table>",
        &HtmlLimits::default(),
    )
    .unwrap();

    let outer_table = element(&document, "table");
    let outer_body = element(outer_table, "tbody");
    let outer_row = element(outer_body, "tr");
    let outer_cell = element(outer_row, "td");
    let inner_table = element(outer_cell, "table");
    let inner_body = element(inner_table, "tbody");
    let inner_row = element(inner_body, "tr");
    let inner_cell = element(inner_row, "td");

    assert_eq!(inner_cell.text_content(), "inner");
    assert_eq!(outer_cell.text_content(), "outerinnertail");
}

#[test]
fn cells_outside_table_sections_do_not_gain_an_implied_row() {
    let document = parse_document(b"<div><td>outside</td></div>", &HtmlLimits::default()).unwrap();

    let container = element(&document, "div");
    assert_eq!(container.children.len(), 1);
    assert_eq!(container.children[0].tag_name(), Some("td"));
    assert_eq!(container.children[0].text_content(), "outside");
}


#[test]
fn table_section_names_outside_a_table_do_not_create_implied_rows() {
    let document = parse_document(
        b"<div><tbody><td>outside</td></tbody></div>",
        &HtmlLimits::default(),
    )
    .unwrap();

    let container = element(&document, "div");
    let section = element(container, "tbody");
    assert_eq!(section.children.len(), 1);
    assert_eq!(section.children[0].tag_name(), Some("td"));
    assert_eq!(section.children[0].text_content(), "outside");
}

#[test]
fn cell_closure_does_not_run_outside_a_table_section() {
    let document = parse_document(
        b"<div><tr><td>first<th>second</th></tr></div>",
        &HtmlLimits::default(),
    )
    .unwrap();

    let container = element(&document, "div");
    let row = element(container, "tr");
    assert_eq!(row.children.len(), 1);

    let cell = element(row, "td");
    assert_eq!(cell.children.len(), 2);
    assert_eq!(cell.children[1].tag_name(), Some("th"));
    assert_eq!(cell.children[1].text_content(), "second");
}

#[test]
fn row_closure_does_not_run_outside_a_table_section() {
    let document = parse_document(
        b"<div><tr><td>first<tr><td>second</td></tr></div>",
        &HtmlLimits::default(),
    )
    .unwrap();

    let container = element(&document, "div");
    let row = element(container, "tr");
    assert_eq!(row.children.len(), 1);

    let cell = element(row, "td");
    assert_eq!(cell.children.len(), 2);
    assert_eq!(cell.children[1].tag_name(), Some("tr"));
    assert_eq!(element(cell, "tr").text_content(), "second");
}

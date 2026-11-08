//! Regression coverage for the HTML iframe RAWTEXT tokenizer state.

use hari_engine_html::{HtmlLimits, parse_document};

fn parse(source: &str) -> hari_engine_html::Node {
    parse_document(source.as_bytes(), &HtmlLimits::default()).expect("valid bounded HTML")
}

#[test]
fn iframe_content_is_text_and_character_references_are_not_decoded() {
    let document = parse("<iframe><b>literal</b> &amp; text</iframe><p>after</p>");

    assert_eq!(document.text_content(), "<b>literal</b> &amp; textafter");
    assert_eq!(document.children.len(), 2);
    assert!(matches!(
        &document.children[0].kind,
        hari_engine_html::NodeKind::Element { tag_name } if tag_name == "iframe"
    ));
    assert!(matches!(
        &document.children[0].children[..],
        [hari_engine_html::Node { kind: hari_engine_html::NodeKind::Text(value), .. }]
            if value == "<b>literal</b> &amp; text"
    ));
}

#[test]
fn iframe_end_tag_is_ascii_case_insensitive_and_resumes_data_parsing() {
    let document = parse("<iframe>inside</IFRAME><p>after</p>");

    assert_eq!(document.text_content(), "insideafter");
    assert_eq!(document.children.len(), 2);
    assert!(matches!(
        &document.children[1].kind,
        hari_engine_html::NodeKind::Element { tag_name } if tag_name == "p"
    ));
}

#[test]
fn iframe_name_prefix_is_not_mistaken_for_an_end_tag() {
    let document = parse("<iframe>inside</iframex><p>after</p>");

    assert_eq!(document.text_content(), "inside</iframex><p>after</p>");
    assert_eq!(document.children.len(), 1);
    assert_eq!(document.children[0].children.len(), 1);
}

#[test]
fn unterminated_iframe_keeps_the_remaining_markup_as_text() {
    let document = parse("<iframe>unfinished <b>still text");

    assert_eq!(document.text_content(), "unfinished <b>still text");
    assert_eq!(document.children.len(), 1);
    assert_eq!(document.children[0].children.len(), 1);
}

#[test]
fn empty_iframe_closes_without_creating_an_empty_text_node() {
    let document = parse("<iframe></iframe><p>after</p>");

    assert_eq!(document.text_content(), "after");
    assert!(document.children[0].children.is_empty());
    assert_eq!(document.children.len(), 2);
}

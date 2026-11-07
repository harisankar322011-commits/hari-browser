//! Regression coverage for the HTML initial-LF suppression rule.

use hari_engine_html::{HtmlLimits, parse_document};

fn parse_text_content(source: &str) -> String {
    parse_document(source.as_bytes(), &HtmlLimits::default())
        .expect("valid HTML input")
        .text_content()
}

#[test]
fn pre_textarea_and_listing_suppress_one_immediate_lf() {
    for tag_name in ["pre", "textarea", "listing"] {
        let source = format!("<{tag_name}>\nfirst\nsecond</{tag_name}>");
        assert_eq!(
            parse_text_content(&source),
            "first\nsecond",
            "{tag_name} must suppress exactly its first LF"
        );
    }
}

#[test]
fn suppression_removes_only_one_lf_and_preserves_the_next() {
    assert_eq!(parse_text_content("<pre>\n\ntext</pre>"), "\ntext");
}

#[test]
fn suppression_requires_lf_to_be_the_immediate_next_character() {
    assert_eq!(parse_text_content("<pre> \ntext</pre>"), " \ntext");
    assert_eq!(
        parse_text_content("<pre><!-- comment -->\ntext</pre>"),
        "\ntext"
    );
    assert_eq!(parse_text_content("<div>\ntext</div>"), "\ntext");
}

#[test]
fn textarea_still_decodes_references_after_suppressing_the_initial_lf() {
    assert_eq!(parse_text_content("<textarea>\n&amp;\n</textarea>"), "&\n");
}

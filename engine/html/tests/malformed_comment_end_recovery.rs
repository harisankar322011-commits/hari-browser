use hari_engine_html::{HtmlLimits, NodeKind, parse_document};

fn assert_comment_between_foo_and_baz(source: &[u8], expected_comment: &str) {
    let document = parse_document(source, &HtmlLimits::default()).unwrap();

    assert_eq!(
        document.children.len(),
        3,
        "the malformed comment terminator must not consume the trailing text"
    );
    assert!(matches!(
        &document.children[0].kind,
        NodeKind::Text(value) if value == "FOO"
    ));
    assert!(matches!(
        &document.children[1].kind,
        NodeKind::Comment(value) if value == expected_comment
    ));
    assert!(matches!(
        &document.children[2].kind,
        NodeKind::Text(value) if value == "BAZ"
    ));
}

#[test]
fn incorrectly_closed_comment_ends_at_double_dash_bang_greater_than() {
    assert_comment_between_foo_and_baz(b"FOO<!-- BAR --!>BAZ", " BAR ");
}

#[test]
fn incorrectly_closed_comment_preserves_content_before_its_terminator() {
    assert_comment_between_foo_and_baz(
        b"FOO<!-- BAR -- <QUX> -- MUX --!>BAZ",
        " BAR -- <QUX> -- MUX ",
    );
}

#[test]
fn abruptly_closed_empty_comment_after_one_dash_is_empty() {
    assert_comment_between_foo_and_baz(b"FOO<!-->BAZ", "");
}

#[test]
fn abruptly_closed_empty_comment_after_two_dashes_is_empty() {
    assert_comment_between_foo_and_baz(b"FOO<!--->BAZ", "");
}

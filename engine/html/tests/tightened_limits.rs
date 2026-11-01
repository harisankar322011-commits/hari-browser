//! Regression tests proving callers can lower parser resource budgets.
//!
//! Absolute ceilings are tested separately. These cases verify that a caller's
//! stricter per-document limits are still honored by the effective limits.

use hari_engine_core::InputError;
use hari_engine_html::{HtmlLimits, ParseError, parse_document};

#[test]
fn caller_can_tighten_the_document_byte_limit() {
    let source = b"<p>hello</p>";
    let limits = HtmlLimits {
        max_document_bytes: source.len() - 1,
        ..HtmlLimits::default()
    };

    assert_eq!(
        parse_document(source, &limits),
        Err(ParseError::Input(InputError::DocumentTooLarge))
    );
}

#[test]
fn caller_can_tighten_the_node_limit() {
    let source = b"<a></a><b></b>";
    let limits = HtmlLimits {
        max_nodes: 1,
        ..HtmlLimits::default()
    };

    assert_eq!(
        parse_document(source, &limits),
        Err(ParseError::NodeLimitExceeded)
    );
}

#[test]
fn caller_can_tighten_the_nesting_depth_limit() {
    let source = b"<a><b></b></a>";
    let limits = HtmlLimits {
        max_depth: 1,
        ..HtmlLimits::default()
    };

    assert_eq!(
        parse_document(source, &limits),
        Err(ParseError::DepthLimitExceeded)
    );
}

#[test]
fn caller_can_tighten_the_per_element_attribute_limit() {
    let source = b"<x a='1' b='2'>";
    let limits = HtmlLimits {
        max_attributes_per_element: 1,
        ..HtmlLimits::default()
    };

    assert_eq!(
        parse_document(source, &limits),
        Err(ParseError::AttributeLimitExceeded)
    );
}

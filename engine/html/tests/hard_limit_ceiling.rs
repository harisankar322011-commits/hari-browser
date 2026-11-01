//! Regression tests for the parser's absolute resource ceilings.
//!
//! Public limits may tighten the budgets, but callers must not be able to
//! raise the parser's hard safety ceilings with unusually large configuration
//! values.

use hari_engine_html::{HtmlLimits, ParseError, parse_document};

#[test]
fn caller_cannot_raise_the_hard_document_byte_ceiling() {
    let source = vec![b'x'; 1_048_577];
    let limits = HtmlLimits {
        max_document_bytes: usize::MAX,
        ..HtmlLimits::default()
    };

    assert!(matches!(
        parse_document(&source, &limits),
        Err(ParseError::Input(_))
    ));
}

#[test]
fn caller_cannot_raise_the_hard_node_ceiling() {
    // Each pair creates one element node while keeping nesting shallow and
    // the complete source below the hard document-byte ceiling.
    let source = "<x></x>".repeat(100_001);
    let limits = HtmlLimits {
        max_nodes: usize::MAX,
        ..HtmlLimits::default()
    };

    assert_eq!(
        parse_document(source.as_bytes(), &limits),
        Err(ParseError::NodeLimitExceeded)
    );
}

#[test]
fn caller_cannot_raise_the_hard_element_depth_ceiling() {
    let source = "<x>".repeat(257);
    let limits = HtmlLimits {
        max_depth: usize::MAX,
        ..HtmlLimits::default()
    };

    assert_eq!(
        parse_document(source.as_bytes(), &limits),
        Err(ParseError::DepthLimitExceeded)
    );
}

#[test]
fn caller_cannot_raise_the_hard_attribute_ceiling() {
    let mut source = String::from("<x");
    for index in 0..257 {
        source.push_str(&format!(" a{index}='v'"));
    }
    source.push('>');

    let limits = HtmlLimits {
        max_attributes_per_element: usize::MAX,
        ..HtmlLimits::default()
    };

    assert_eq!(
        parse_document(source.as_bytes(), &limits),
        Err(ParseError::AttributeLimitExceeded)
    );
}

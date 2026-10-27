//! Regression tests for bounded stylesheet parsing in the experimental HARI Engine.

use hari_engine_layout::{CssError, StyleSheet};

const MAX_CSS_BYTES: usize = 256 * 1024;
const MAX_CSS_RULES: usize = 128;
const MAX_DECLARATIONS_PER_RULE: usize = 64;

#[test]
fn rejects_stylesheet_over_byte_budget_before_trimming_or_parsing() {
    // Whitespace is still input and must count against the byte budget.
    let source = " ".repeat(MAX_CSS_BYTES + 1);
    assert_eq!(StyleSheet::parse(&source), Err(CssError::StylesheetTooLarge));
}

#[test]
fn rejects_too_many_css_rules() {
    let source = "p { display: block; }".repeat(MAX_CSS_RULES + 1);
    assert_eq!(StyleSheet::parse(&source), Err(CssError::RuleLimitExceeded));
}

#[test]
fn rejects_too_many_declarations_in_one_rule() {
    let declarations = "display: block;".repeat(MAX_DECLARATIONS_PER_RULE + 1);
    let source = format!("p {{ {declarations} }}");
    assert_eq!(
        StyleSheet::parse(&source),
        Err(CssError::DeclarationLimitExceeded)
    );
}

#[test]
fn accepts_rule_and_declaration_counts_at_the_configured_limits() {
    let rule = "p { display: block; }".repeat(MAX_CSS_RULES);
    assert!(StyleSheet::parse(&rule).is_ok());

    let declarations = "display: block;".repeat(MAX_DECLARATIONS_PER_RULE);
    let source = format!("p {{ {declarations} }}");
    assert!(StyleSheet::parse(&source).is_ok());
}

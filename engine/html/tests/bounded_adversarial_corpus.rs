//! Deterministic generated hostile-input coverage for the bounded HARI HTML parser.
//!
//! This is a lightweight regression corpus, not a replacement for coverage-guided
//! fuzzing or HTML-standard conformance tests.

use hari_engine_html::{HtmlLimits, Node, NodeKind, parse_document};

const ALPHABET: &[char] = &[
    '<', '>', '/', '!', '-', '=', '\'', '"', '&', '#', ';', 'a', 'A', 'b', 'c', 'p', 's',
    't', 'r', 'i', ' ', '\t', '\n', 'é', 'മ', '😀', '\u{00A0}',
];

fn assert_bounded_tree(node: &Node, parent_element_depth: usize, limits: &HtmlLimits) -> usize {
    let element_depth = match &node.kind {
        NodeKind::Element { tag_name } => {
            assert_eq!(
                tag_name,
                &tag_name.to_ascii_lowercase(),
                "element names must be normalized"
            );
            assert!(
                node.attributes.len() <= limits.max_attributes_per_element,
                "element exceeded the configured attribute budget"
            );
            for attribute in &node.attributes {
                assert_eq!(
                    attribute.name,
                    attribute.name.to_ascii_lowercase(),
                    "attribute names must be normalized"
                );
            }
            parent_element_depth + 1
        }
        NodeKind::Document => {
            assert!(node.attributes.is_empty());
            assert_eq!(parent_element_depth, 0);
            parent_element_depth
        }
        NodeKind::Doctype(_) | NodeKind::Comment(_) | NodeKind::Text(_) => {
            assert!(node.attributes.is_empty());
            assert!(
                node.children.is_empty(),
                "non-container nodes must not acquire children"
            );
            parent_element_depth
        }
    };

    assert!(
        element_depth <= limits.max_depth,
        "tree exceeded the configured element-depth budget"
    );

    1 + node
        .children
        .iter()
        .map(|child| assert_bounded_tree(child, element_depth, limits))
        .sum::<usize>()
}

#[test]
fn deterministic_generated_markup_keeps_parser_output_bounded() {
    let limits = HtmlLimits {
        max_document_bytes: 2_048,
        max_nodes: 128,
        max_depth: 24,
        max_attributes_per_element: 16,
    };

    // Fixed seed means a failing case can be reproduced exactly in CI.
    let mut state = 0x4841_5249_u32;
    let mut accepted = 0usize;

    for case_index in 0..512 {
        state = state
            .wrapping_mul(1_664_525)
            .wrapping_add(1_013_904_223);
        let character_count = (state % 256) as usize;
        let mut source = String::with_capacity(character_count * 2);

        for _ in 0..character_count {
            state = state
                .wrapping_mul(1_664_525)
                .wrapping_add(1_013_904_223);
            source.push(ALPHABET[(state as usize) % ALPHABET.len()]);
        }

        assert!(
            source.len() <= limits.max_document_bytes,
            "generated case {case_index} exceeded the byte budget"
        );

        match parse_document(source.as_bytes(), &limits) {
            Ok(document) => {
                accepted += 1;
                let node_count = assert_bounded_tree(&document, 0, &limits);
                assert!(
                    node_count <= limits.max_nodes + 1,
                    "generated case {case_index} exceeded the node budget"
                );
            }
            Err(_) => {
                // Resource-limit or input-boundary rejection is valid for hostile
                // generated markup; the important property is deterministic, bounded
                // termination without a panic or an over-budget tree.
            }
        }
    }

    assert!(
        accepted >= 128,
        "too few generated inputs exercised the successful parse path: {accepted}/512"
    );
}

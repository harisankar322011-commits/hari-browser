#![no_main]

use hari_engine_html::{HtmlLimits, Node, NodeKind, parse_document};
use libfuzzer_sys::fuzz_target;

const MAX_FUZZ_INPUT_BYTES: usize = 16 * 1024;
const MAX_FUZZ_NODES: usize = 1_024;
const MAX_FUZZ_DEPTH: usize = 64;
const MAX_FUZZ_ATTRIBUTES: usize = 64;

fn assert_tree_invariants(root: &Node, limits: &HtmlLimits) {
    // Iterative traversal keeps the harness itself safe on unexpected output.
    let mut pending = vec![(root, 0usize)];
    let mut node_count = 0usize;

    while let Some((node, parent_element_depth)) = pending.pop() {
        node_count += 1;
        assert!(
            node_count <= limits.max_nodes + 1,
            "parser returned more nodes than its configured budget"
        );
        assert!(
            node.attributes.len() <= limits.max_attributes_per_element,
            "parser returned an element above its attribute budget"
        );

        let element_depth = match &node.kind {
            NodeKind::Element { tag_name } => {
                assert_eq!(
                    tag_name,
                    &tag_name.to_ascii_lowercase(),
                    "element names must be normalized"
                );
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
                    "non-container nodes must not have children"
                );
                parent_element_depth
            }
        };

        assert!(
            element_depth <= limits.max_depth,
            "parser returned a tree over its depth budget"
        );

        for attribute in &node.attributes {
            assert_eq!(
                attribute.name,
                attribute.name.to_ascii_lowercase(),
                "attribute names must be normalized"
            );
        }

        for child in node.children.iter().rev() {
            pending.push((child, element_depth));
        }
    }
}

fuzz_target!(|data: &[u8]| {
    let limits = HtmlLimits {
        max_document_bytes: MAX_FUZZ_INPUT_BYTES,
        max_nodes: MAX_FUZZ_NODES,
        max_depth: MAX_FUZZ_DEPTH,
        max_attributes_per_element: MAX_FUZZ_ATTRIBUTES,
    };

    if let Ok(document) = parse_document(data, &limits) {
        assert_tree_invariants(&document, &limits);
    }
});

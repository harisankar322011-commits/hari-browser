//! HARI DOM behavior tests are written before the implementation.

#[cfg(test)]
mod tests {
    use super::{DomDocument, DomError, DomLimits, DocumentState, NodeId};
    use hari_engine_html::NodeKind;

    fn child_tag(document: &DomDocument, parent: NodeId, expected: &str) -> NodeId {
        document
            .children(parent)
            .unwrap()
            .iter()
            .copied()
            .find(|id| {
                document.node(*id).unwrap().tag_name() == Some(expected)
            })
            .unwrap_or_else(|| panic!("missing child element {expected}"))
    }

    #[test]
    fn imports_parsed_html_and_preserves_parent_links() {
        let mut document = DomDocument::from_html(
            b"<html><body><h1>Hello</h1><p>World</p></body></html>",
            &DomLimits::default(),
        )
        .unwrap();

        assert_eq!(document.state(), DocumentState::Ready);
        let root = document.root();
        let html = child_tag(&document, root, "html");
        let body = child_tag(&document, html, "body");
        let heading = child_tag(&document, body, "h1");
        assert_eq!(document.node(heading).unwrap().text_content(), "Hello");
        assert_eq!(document.node(heading).unwrap().parent(), Some(body));
        document.validate_invariants().unwrap();
    }

    #[test]
    fn append_child_sets_parent_and_remove_child_detaches_without_deleting_node() {
        let mut document = DomDocument::new(DomLimits::default());
        let body = document.create_element("body").unwrap();
        let text = document.create_text("Hello").unwrap();

        document.append_child(document.root(), body).unwrap();
        document.append_child(body, text).unwrap();
        assert_eq!(document.node(text).unwrap().parent(), Some(body));

        document.remove_child(body, text).unwrap();
        assert_eq!(document.node(text).unwrap().parent(), None);
        assert!(document.children(body).unwrap().is_empty());
        assert_eq!(document.node(text).unwrap().text_content(), "Hello");
        document.validate_invariants().unwrap();
    }

    #[test]
    fn moving_a_node_updates_both_parent_child_lists() {
        let mut document = DomDocument::new(DomLimits::default());
        let left = document.create_element("left").unwrap();
        let right = document.create_element("right").unwrap();
        let item = document.create_element("item").unwrap();
        document.append_child(document.root(), left).unwrap();
        document.append_child(document.root(), right).unwrap();
        document.append_child(left, item).unwrap();

        document.append_child(right, item).unwrap();

        assert!(document.children(left).unwrap().is_empty());
        assert_eq!(document.children(right).unwrap(), &[item]);
        assert_eq!(document.node(item).unwrap().parent(), Some(right));
        document.validate_invariants().unwrap();
    }

    #[test]
    fn rejects_cycles_and_preserves_original_tree() {
        let mut document = DomDocument::new(DomLimits::default());
        let parent = document.create_element("parent").unwrap();
        let child = document.create_element("child").unwrap();
        document.append_child(document.root(), parent).unwrap();
        document.append_child(parent, child).unwrap();

        assert_eq!(
            document.append_child(child, parent),
            Err(DomError::CycleDetected)
        );
        assert_eq!(document.node(parent).unwrap().parent(), Some(document.root()));
        assert_eq!(document.node(child).unwrap().parent(), Some(parent));
        document.validate_invariants().unwrap();
    }

    #[test]
    fn refuses_to_attach_document_node_or_parent_text_nodes() {
        let mut document = DomDocument::new(DomLimits::default());
        let text = document.create_text("text").unwrap();
        let element = document.create_element("p").unwrap();

        assert_eq!(
            document.append_child(element, document.root()),
            Err(DomError::CannotMoveDocumentRoot)
        );
        document.append_child(document.root(), element).unwrap();
        document.append_child(document.root(), text).unwrap();
        assert_eq!(
            document.append_child(text, element),
            Err(DomError::CannotHaveChildren)
        );
    }

    #[test]
    fn rejects_invalid_node_ids() {
        let document = DomDocument::new(DomLimits::default());
        assert_eq!(document.node(NodeId(999)), Err(DomError::InvalidNode));
        assert_eq!(document.children(NodeId(999)), Err(DomError::InvalidNode));
    }

    #[test]
    fn refuses_mutation_after_document_is_closed() {
        let mut document = DomDocument::new(DomLimits::default());
        document.close().unwrap();
        assert_eq!(document.state(), DocumentState::Closed);
        assert_eq!(
            document.create_element("p"),
            Err(DomError::DocumentClosed)
        );
    }

    #[test]
    fn enforces_node_limit() {
        let limits = DomLimits { max_nodes: 2, ..DomLimits::default() };
        let mut document = DomDocument::new(limits);
        document.create_element("one").unwrap();
        assert_eq!(
            document.create_text("too many"),
            Err(DomError::NodeLimitExceeded)
        );
    }

    #[test]
    fn enforces_depth_limit_when_appending_subtree() {
        let limits = DomLimits { max_depth: 2, ..DomLimits::default() };
        let mut document = DomDocument::new(limits);
        let first = document.create_element("a").unwrap();
        let second = document.create_element("b").unwrap();
        let third = document.create_element("c").unwrap();

        document.append_child(document.root(), first).unwrap();
        document.append_child(first, second).unwrap();
        assert_eq!(
            document.append_child(second, third),
            Err(DomError::DepthLimitExceeded)
        );
    }

    #[test]
    fn document_node_kind_is_fixed_and_root_has_no_parent() {
        let document = DomDocument::new(DomLimits::default());
        let root = document.node(document.root()).unwrap();
        assert_eq!(root.parent(), None);
        assert!(matches!(root.kind(), NodeKind::Document));
    }
}

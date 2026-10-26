//! HARI DOM — an isolated, arena-backed tree model.
//! This is a small project-specific DOM foundation, not a standards-compliant browser DOM.

use hari_engine_html::{
    Attribute, HtmlLimits, Node as HtmlNode, NodeKind, ParseError, parse_document,
};

const HARD_MAX_NODES: usize = 100_000;
const HARD_MAX_DEPTH: usize = 512;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct NodeId(pub usize);

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct DomLimits {
    /// Includes the document root.
    pub max_nodes: usize,
    /// Root depth is zero; each child edge adds one.
    pub max_depth: usize,
}

impl Default for DomLimits {
    fn default() -> Self {
        Self {
            max_nodes: HARD_MAX_NODES,
            max_depth: HARD_MAX_DEPTH,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DocumentState {
    Building,
    Ready,
    Closed,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DomError {
    Html(ParseError),
    InvalidNode,
    InvalidName,
    DocumentClosed,
    InvalidLifecycle,
    CannotMoveDocumentRoot,
    CannotHaveChildren,
    NotAChild,
    CycleDetected,
    NodeLimitExceeded,
    DepthLimitExceeded,
    InvariantViolation,
}

/// Node links remain private so the document maintains parent/child consistency.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DomNode {
    kind: NodeKind,
    attributes: Vec<Attribute>,
    parent: Option<NodeId>,
    children: Vec<NodeId>,
}

impl DomNode {
    pub fn kind(&self) -> &NodeKind {
        &self.kind
    }

    pub fn tag_name(&self) -> Option<&str> {
        match &self.kind {
            NodeKind::Element { tag_name } => Some(tag_name),
            _ => None,
        }
    }

    pub fn attributes(&self) -> &[Attribute] {
        &self.attributes
    }

    pub fn attribute(&self, name: &str) -> Option<&str> {
        self.attributes
            .iter()
            .find(|attribute| attribute.name.eq_ignore_ascii_case(name))
            .map(|attribute| attribute.value.as_str())
    }

    pub fn parent(&self) -> Option<NodeId> {
        self.parent
    }

    pub fn children(&self) -> &[NodeId] {
        &self.children
    }

    pub fn text_content(&self, document: &DomDocument) -> String {
        fn append(node: &DomNode, document: &DomDocument, output: &mut String) {
            if let NodeKind::Text(value) = &node.kind {
                output.push_str(value);
            }
            for child in &node.children {
                if let Ok(child_node) = document.node(*child) {
                    append(child_node, document, output);
                }
            }
        }

        let mut output = String::new();
        append(self, document, &mut output);
        output
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DomDocument {
    nodes: Vec<DomNode>,
    limits: DomLimits,
    state: DocumentState,
}

impl DomDocument {
    pub fn new(limits: DomLimits) -> Self {
        let limits = DomLimits {
            max_nodes: limits.max_nodes.clamp(1, HARD_MAX_NODES),
            max_depth: limits.max_depth.min(HARD_MAX_DEPTH),
        };
        Self {
            nodes: vec![DomNode {
                kind: NodeKind::Document,
                attributes: Vec::new(),
                parent: None,
                children: Vec::new(),
            }],
            limits,
            state: DocumentState::Building,
        }
    }

    pub fn from_html(bytes: &[u8], limits: &DomLimits) -> Result<Self, DomError> {
        let parsed = parse_document(bytes, &HtmlLimits::default()).map_err(DomError::Html)?;
        let mut document = Self::new(*limits);
        for child in &parsed.children {
            document.import_subtree(child, document.root())?;
        }
        document.finish_loading()?;
        Ok(document)
    }

    pub fn root(&self) -> NodeId {
        NodeId(0)
    }
    pub fn state(&self) -> DocumentState {
        self.state
    }
    pub fn limits(&self) -> DomLimits {
        self.limits
    }

    pub fn node(&self, id: NodeId) -> Result<&DomNode, DomError> {
        self.nodes.get(id.0).ok_or(DomError::InvalidNode)
    }

    pub fn children(&self, id: NodeId) -> Result<&[NodeId], DomError> {
        Ok(self.node(id)?.children())
    }

    pub fn create_element(&mut self, name: &str) -> Result<NodeId, DomError> {
        if name.is_empty()
            || name.len() > 256
            || !name
                .bytes()
                .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'-' | b'_' | b':'))
        {
            return Err(DomError::InvalidName);
        }
        self.create_node(
            NodeKind::Element {
                tag_name: name.to_ascii_lowercase(),
            },
            Vec::new(),
        )
    }

    pub fn create_text(&mut self, value: &str) -> Result<NodeId, DomError> {
        self.create_node(NodeKind::Text(value.to_owned()), Vec::new())
    }

    pub fn create_comment(&mut self, value: &str) -> Result<NodeId, DomError> {
        self.create_node(NodeKind::Comment(value.to_owned()), Vec::new())
    }

    fn create_node(
        &mut self,
        kind: NodeKind,
        attributes: Vec<Attribute>,
    ) -> Result<NodeId, DomError> {
        self.ensure_mutable()?;
        if self.nodes.len() >= self.limits.max_nodes {
            return Err(DomError::NodeLimitExceeded);
        }
        let id = NodeId(self.nodes.len());
        self.nodes.push(DomNode {
            kind,
            attributes,
            parent: None,
            children: Vec::new(),
        });
        Ok(id)
    }

    fn import_subtree(&mut self, source: &HtmlNode, parent: NodeId) -> Result<NodeId, DomError> {
        let id = self.create_node(source.kind.clone(), source.attributes.clone())?;
        self.append_child(parent, id)?;
        for child in &source.children {
            self.import_subtree(child, id)?;
        }
        Ok(id)
    }

    /// Attaches or reparents a node. Validates cycle and depth constraints before mutation.
    pub fn append_child(&mut self, parent: NodeId, child: NodeId) -> Result<(), DomError> {
        self.ensure_mutable()?;
        let parent_node = self.node(parent)?;
        let child_node = self.node(child)?;

        if child == self.root() || matches!(&child_node.kind, NodeKind::Document) {
            return Err(DomError::CannotMoveDocumentRoot);
        }
        if !matches!(
            &parent_node.kind,
            NodeKind::Document | NodeKind::Element { .. }
        ) {
            return Err(DomError::CannotHaveChildren);
        }
        if child == parent {
            return Err(DomError::CycleDetected);
        }
        if self.nodes[parent.0].children.contains(&child) {
            return Ok(());
        }

        let mut ancestor = Some(parent);
        while let Some(ancestor_id) = ancestor {
            if ancestor_id == child {
                return Err(DomError::CycleDetected);
            }
            ancestor = self.node(ancestor_id)?.parent;
        }

        let target_depth = self.depth_of(parent)?.saturating_add(1);
        let subtree_height = self.subtree_height(child)?;
        if target_depth.saturating_add(subtree_height) > self.limits.max_depth {
            return Err(DomError::DepthLimitExceeded);
        }

        if let Some(old_parent) = self.nodes[child.0].parent {
            let old_children = &mut self.nodes[old_parent.0].children;
            let old_index = old_children
                .iter()
                .position(|candidate| *candidate == child)
                .ok_or(DomError::InvariantViolation)?;
            old_children.remove(old_index);
        }
        self.nodes[child.0].parent = Some(parent);
        self.nodes[parent.0].children.push(child);
        Ok(())
    }

    /// Detaches a child but preserves its arena node for future reattachment.
    pub fn remove_child(&mut self, parent: NodeId, child: NodeId) -> Result<(), DomError> {
        self.ensure_mutable()?;
        self.node(parent)?;
        self.node(child)?;
        if child == self.root() {
            return Err(DomError::CannotMoveDocumentRoot);
        }
        if self.nodes[child.0].parent != Some(parent) {
            return Err(DomError::NotAChild);
        }

        let children = &mut self.nodes[parent.0].children;
        let position = children
            .iter()
            .position(|candidate| *candidate == child)
            .ok_or(DomError::InvariantViolation)?;
        children.remove(position);
        self.nodes[child.0].parent = None;
        Ok(())
    }

    pub fn finish_loading(&mut self) -> Result<(), DomError> {
        self.ensure_mutable()?;
        if self.state != DocumentState::Building {
            return Err(DomError::InvalidLifecycle);
        }
        self.validate_invariants()?;
        self.state = DocumentState::Ready;
        Ok(())
    }

    pub fn close(&mut self) -> Result<(), DomError> {
        self.state = DocumentState::Closed;
        Ok(())
    }

    pub fn validate_invariants(&self) -> Result<(), DomError> {
        if self.nodes.is_empty()
            || !matches!(&self.nodes[0].kind, NodeKind::Document)
            || self.nodes[0].parent.is_some()
        {
            return Err(DomError::InvariantViolation);
        }

        for (index, node) in self.nodes.iter().enumerate() {
            let id = NodeId(index);
            if index != 0 && matches!(&node.kind, NodeKind::Document) {
                return Err(DomError::InvariantViolation);
            }
            if !matches!(&node.kind, NodeKind::Document | NodeKind::Element { .. })
                && !node.children.is_empty()
            {
                return Err(DomError::CannotHaveChildren);
            }

            for (offset, child) in node.children.iter().enumerate() {
                let child_node = self.node(*child)?;
                if child_node.parent != Some(id)
                    || node.children[..offset].contains(child)
                    || *child == self.root()
                {
                    return Err(DomError::InvariantViolation);
                }
            }

            if let Some(parent) = node.parent
                && !self.node(parent)?.children.contains(&id)
            {
                return Err(DomError::InvariantViolation);
            }
            if self.depth_of(id)? > self.limits.max_depth {
                return Err(DomError::DepthLimitExceeded);
            }
        }
        Ok(())
    }

    fn ensure_mutable(&self) -> Result<(), DomError> {
        if self.state == DocumentState::Closed {
            Err(DomError::DocumentClosed)
        } else {
            Ok(())
        }
    }

    fn depth_of(&self, id: NodeId) -> Result<usize, DomError> {
        self.node(id)?;
        let mut depth = 0usize;
        let mut current = self.nodes[id.0].parent;
        let mut hops = 0usize;
        while let Some(parent) = current {
            depth = depth.saturating_add(1);
            hops = hops.saturating_add(1);
            if hops > self.nodes.len() {
                return Err(DomError::CycleDetected);
            }
            current = self.node(parent)?.parent;
        }
        Ok(depth)
    }

    fn subtree_height(&self, root: NodeId) -> Result<usize, DomError> {
        fn height(
            document: &DomDocument,
            id: NodeId,
            traversed: &mut usize,
        ) -> Result<usize, DomError> {
            *traversed = traversed.saturating_add(1);
            if *traversed > document.nodes.len() {
                return Err(DomError::CycleDetected);
            }
            let node = document.node(id)?;
            let mut max_child_height = 0usize;
            for child in &node.children {
                max_child_height =
                    max_child_height.max(height(document, *child, traversed)?.saturating_add(1));
            }
            Ok(max_child_height)
        }

        let mut traversed = 0usize;
        height(self, root, &mut traversed)
    }
}

#[cfg(test)]
mod tests {
    use super::{DocumentState, DomDocument, DomError, DomLimits, NodeId};
    use hari_engine_html::NodeKind;

    fn child_tag(document: &DomDocument, parent: NodeId, expected: &str) -> NodeId {
        document
            .children(parent)
            .unwrap()
            .iter()
            .copied()
            .find(|id| document.node(*id).unwrap().tag_name() == Some(expected))
            .unwrap_or_else(|| panic!("missing child element {expected}"))
    }

    #[test]
    fn imports_parsed_html_and_preserves_parent_links() {
        let document = DomDocument::from_html(
            b"<html><body><h1>Hello</h1><p>World</p></body></html>",
            &DomLimits::default(),
        )
        .unwrap();
        assert_eq!(document.state(), DocumentState::Ready);
        let root = document.root();
        let html = child_tag(&document, root, "html");
        let body = child_tag(&document, html, "body");
        let heading = child_tag(&document, body, "h1");
        assert_eq!(
            document.node(heading).unwrap().text_content(&document),
            "Hello"
        );
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
        assert_eq!(
            document.node(text).unwrap().text_content(&document),
            "Hello"
        );
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
        assert_eq!(
            document.node(parent).unwrap().parent(),
            Some(document.root())
        );
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
        assert_eq!(
            document.node(NodeId(999)).map(|n| n.kind()),
            Err(DomError::InvalidNode)
        );
        assert_eq!(document.children(NodeId(999)), Err(DomError::InvalidNode));
    }

    #[test]
    fn refuses_mutation_after_document_is_closed() {
        let mut document = DomDocument::new(DomLimits::default());
        document.close().unwrap();
        assert_eq!(document.state(), DocumentState::Closed);
        assert_eq!(document.create_element("p"), Err(DomError::DocumentClosed));
    }

    #[test]
    fn enforces_node_limit() {
        let limits = DomLimits {
            max_nodes: 2,
            ..DomLimits::default()
        };
        let mut document = DomDocument::new(limits);
        document.create_element("one").unwrap();
        assert_eq!(
            document.create_text("too many"),
            Err(DomError::NodeLimitExceeded)
        );
    }

    #[test]
    fn enforces_depth_limit_when_appending_subtree() {
        let limits = DomLimits {
            max_depth: 2,
            ..DomLimits::default()
        };
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

    #[test]
    fn rejects_invalid_element_names() {
        let mut document = DomDocument::new(DomLimits::default());
        assert_eq!(
            document.create_element("not>valid"),
            Err(DomError::InvalidName)
        );
    }
}

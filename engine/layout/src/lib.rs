//! HARI CSS/layout tests are written before the implementation.

#[cfg(test)]
mod tests {
    use super::{
        compute_layout, BoxKind, CssError, Display, LayoutError, LayoutOptions, Rect, StyleSheet,
    };
    use hari_engine_dom::{DomDocument, DomLimits, NodeId};

    fn fixture() -> DomDocument {
        DomDocument::from_html(
            b"<html><body><h1>Hello</h1><p>World</p></body></html>",
            &DomLimits::default(),
        ).unwrap()
    }

    fn find_tag<'a>(layout: &'a super::LayoutBox, name: &str) -> Option<&'a super::LayoutBox> {
        if matches!(&layout.kind, BoxKind::Element { tag_name } if tag_name == name) {
            return Some(layout);
        }
        layout.children.iter().find_map(|child| find_tag(child, name))
    }

    #[test]
    fn lays_out_basic_document_in_vertical_block_flow() {
        let document = fixture();
        let stylesheet = StyleSheet::default();
        let layout = compute_layout(&document, &stylesheet, &LayoutOptions {
            viewport_width: 320,
            ..LayoutOptions::default()
        }).unwrap();

        assert_eq!(layout.node_id, document.root());
        assert_eq!(layout.rect.width, 320);
        let body = find_tag(&layout, "body").unwrap();
        let heading = find_tag(&layout, "h1").unwrap();
        let paragraph = find_tag(&layout, "p").unwrap();
        assert_eq!(heading.rect.y, body.rect.y);
        assert_eq!(paragraph.rect.y, heading.rect.y + heading.rect.height);
        assert!(layout.rect.height >= paragraph.rect.y + paragraph.rect.height);
    }

    #[test]
    fn applies_tag_rules_for_width_height_and_margin() {
        let document = fixture();
        let stylesheet = StyleSheet::parse(
            "body { width: 280px; height: 90px; margin: 4px; }",
        ).unwrap();
        let layout = compute_layout(&document, &stylesheet, &LayoutOptions {
            viewport_width: 320,
            ..LayoutOptions::default()
        }).unwrap();
        let body = find_tag(&layout, "body").unwrap();
        assert_eq!(body.rect, Rect { x: 4, y: 4, width: 280, height: 90 });
    }

    #[test]
    fn display_none_excludes_element_and_descendants_from_layout() {
        let document = fixture();
        let stylesheet = StyleSheet::parse("h1 { display: none; }").unwrap();
        let layout = compute_layout(&document, &stylesheet, &LayoutOptions::default()).unwrap();
        assert!(find_tag(&layout, "h1").is_none());
        assert!(find_tag(&layout, "p").is_some());
    }

    #[test]
    fn wraps_text_to_available_width_using_configured_metrics() {
        let document = DomDocument::from_html(
            b"<p>abcde</p>",
            &DomLimits::default(),
        ).unwrap();
        let layout = compute_layout(&document, &StyleSheet::default(), &LayoutOptions {
            viewport_width: 16,
            character_width: 8,
            default_line_height: 18,
            ..LayoutOptions::default()
        }).unwrap();
        let paragraph = find_tag(&layout, "p").unwrap();
        assert_eq!(paragraph.rect.height, 54);
    }

    #[test]
    fn rejects_unsupported_css_properties_instead_of_silently_lying() {
        assert_eq!(
            StyleSheet::parse("p { position: fixed; }"),
            Err(CssError::UnsupportedProperty)
        );
    }

    #[test]
    fn rejects_invalid_css_lengths() {
        assert_eq!(
            StyleSheet::parse("p { width: -2px; }"),
            Err(CssError::InvalidValue)
        );
    }

    #[test]
    fn rejects_zero_viewport_and_closed_documents() {
        let document = fixture();
        assert_eq!(
            compute_layout(&document, &StyleSheet::default(), &LayoutOptions {
                viewport_width: 0,
                ..LayoutOptions::default()
            }),
            Err(LayoutError::InvalidViewport)
        );

        let mut closed = fixture();
        closed.close().unwrap();
        assert_eq!(
            compute_layout(&closed, &StyleSheet::default(), &LayoutOptions::default()),
            Err(LayoutError::DocumentNotReady)
        );
    }

    #[test]
    fn respects_maximum_generated_layout_box_count() {
        let document = DomDocument::from_html(
            b"<div><p>x</p></div>",
            &DomLimits::default(),
        ).unwrap();
        assert_eq!(
            compute_layout(&document, &StyleSheet::default(), &LayoutOptions {
                max_layout_boxes: 2,
                ..LayoutOptions::default()
            }),
            Err(LayoutError::BoxLimitExceeded)
        );
    }

    #[test]
    fn box_nodes_retain_source_dom_ids() {
        let document = fixture();
        let layout = compute_layout(&document, &StyleSheet::default(), &LayoutOptions::default()).unwrap();
        assert_eq!(layout.node_id, NodeId(0));
        assert!(matches!(layout.kind, BoxKind::Document));
        assert!(matches!(layout.display, Display::Block));
    }
}

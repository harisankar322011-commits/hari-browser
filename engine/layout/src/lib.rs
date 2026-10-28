//! Initial CSS subset and deterministic block-flow layout for HARI Engine.
//!
//! This module is a teaching/prototype pipeline, not a standards-compliant CSS
//! cascade or layout engine. It does not paint pixels, shape fonts, load fonts,
//! collapse whitespace, lay out inline formatting contexts, or support selectors
//! beyond type selectors.

use hari_engine_dom::{DocumentState, DomDocument, DomError, NodeId, NodeKind};

const HARD_MAX_LAYOUT_BOXES: usize = 100_000;
const HARD_MAX_CSS_BYTES: usize = 256 * 1024;
const HARD_MAX_CSS_RULES: usize = 128;
const HARD_MAX_CSS_DECLARATIONS_PER_RULE: usize = 64;
const HARD_MAX_CSS_PX: u32 = 1_000_000;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CssError {
    InvalidRule,
    StylesheetTooLarge,
    RuleLimitExceeded,
    DeclarationLimitExceeded,
    UnsupportedSelector,
    UnsupportedProperty,
    InvalidValue,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LayoutError {
    Dom(DomError),
    DocumentNotReady,
    InvalidViewport,
    InvalidMetrics,
    BoxLimitExceeded,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Display {
    Block,
    None,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Rect {
    pub x: i32,
    pub y: i32,
    pub width: u32,
    pub height: u32,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum BoxKind {
    Document,
    Element { tag_name: String },
    Text(String),
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct LayoutBox {
    pub node_id: NodeId,
    pub kind: BoxKind,
    pub display: Display,
    pub rect: Rect,
    /// Uniform outer margin in this deliberately small layout model.
    pub margin: u32,
    /// Computed or inherited text color, expressed as opaque RGBA.
    pub text_color: Option<[u8; 4]>,
    /// Element background color; backgrounds do not inherit.
    pub background_color: Option<[u8; 4]>,
    pub children: Vec<LayoutBox>,
    flow_advance: u32,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct LayoutOptions {
    pub viewport_width: u32,
    pub default_line_height: u32,
    pub character_width: u32,
    pub max_layout_boxes: usize,
}

impl Default for LayoutOptions {
    fn default() -> Self {
        Self {
            viewport_width: 360,
            default_line_height: 18,
            character_width: 8,
            max_layout_boxes: HARD_MAX_LAYOUT_BOXES,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
struct ElementStyle {
    display: Option<Display>,
    width: Option<u32>,
    height: Option<u32>,
    margin: Option<u32>,
    color: Option<[u8; 4]>,
    background_color: Option<[u8; 4]>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
struct StyleRule {
    selector: String,
    style: ElementStyle,
}

#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct StyleSheet {
    rules: Vec<StyleRule>,
}

impl StyleSheet {
    /// Parses a small CSS subset with type selectors and pixel lengths.
    ///
    /// Supported declarations: `display: block|none`, `width: Npx`,
    /// `height: Npx`, `margin: Npx`, `color`, and `background-color` using
    /// a bounded named-color subset or `#RGB`/`#RRGGBB`. Unsupported CSS is rejected so
    /// callers cannot assume a property was applied when it was ignored.
    pub fn parse(source: &str) -> Result<Self, CssError> {
        // Enforce byte limits before trimming or allocating parsed rule data.
        if source.len() > HARD_MAX_CSS_BYTES {
            return Err(CssError::StylesheetTooLarge);
        }

        let mut remaining = source.trim();
        let mut rules = Vec::new();

        while !remaining.is_empty() {
            if remaining.starts_with("/*") {
                let end = remaining.find("*/").ok_or(CssError::InvalidRule)?;
                remaining = remaining[end + 2..].trim_start();
                continue;
            }

            if rules.len() >= HARD_MAX_CSS_RULES {
                return Err(CssError::RuleLimitExceeded);
            }

            let open = remaining.find('{').ok_or(CssError::InvalidRule)?;
            let selector = remaining[..open].trim();
            if selector.is_empty()
                || !selector
                    .bytes()
                    .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'-' | b'_'))
            {
                return Err(CssError::UnsupportedSelector);
            }

            let close = remaining[open + 1..]
                .find('}')
                .map(|offset| open + 1 + offset)
                .ok_or(CssError::InvalidRule)?;
            let declarations = &remaining[open + 1..close];
            let mut style = ElementStyle::default();
            let mut declaration_count = 0usize;

            for declaration in declarations
                .split(';')
                .map(str::trim)
                .filter(|item| !item.is_empty())
            {
                declaration_count = declaration_count.saturating_add(1);
                if declaration_count > HARD_MAX_CSS_DECLARATIONS_PER_RULE {
                    return Err(CssError::DeclarationLimitExceeded);
                }
                let (property, value) = declaration.split_once(':').ok_or(CssError::InvalidRule)?;
                let property = property.trim().to_ascii_lowercase();
                let value = value.trim().to_ascii_lowercase();
                match property.as_str() {
                    "display" => {
                        style.display = Some(match value.as_str() {
                            "block" => Display::Block,
                            "none" => Display::None,
                            _ => return Err(CssError::InvalidValue),
                        });
                    }
                    "width" => style.width = Some(parse_px(&value)?),
                    "height" => style.height = Some(parse_px(&value)?),
                    "margin" => style.margin = Some(parse_px(&value)?),
                    "color" => style.color = Some(parse_css_color(&value)?),
                    "background-color" => style.background_color = Some(parse_css_color(&value)?),
                    _ => return Err(CssError::UnsupportedProperty),
                }
            }

            rules.push(StyleRule {
                selector: selector.to_ascii_lowercase(),
                style,
            });
            remaining = remaining[close + 1..].trim();
        }

        Ok(Self { rules })
    }

    fn style_for(&self, tag_name: &str) -> ElementStyle {
        // Basic user-agent defaults for elements that do not generate visible boxes.
        // The small author stylesheet subset may override these defaults, just as
        // author CSS can override ordinary user-agent declarations in a browser.
        let default_display = if matches!(
            tag_name,
            "base" | "head" | "link" | "meta" | "script" | "style" | "template" | "title"
        ) {
            Display::None
        } else {
            Display::Block
        };
        let mut computed = ElementStyle {
            display: Some(default_display),
            width: None,
            height: None,
            margin: Some(0),
            color: None,
            background_color: None,
        };
        for rule in &self.rules {
            if rule.selector.eq_ignore_ascii_case(tag_name) {
                if let Some(value) = rule.style.display {
                    computed.display = Some(value);
                }
                if let Some(value) = rule.style.width {
                    computed.width = Some(value);
                }
                if let Some(value) = rule.style.height {
                    computed.height = Some(value);
                }
                if let Some(value) = rule.style.margin {
                    computed.margin = Some(value);
                }
                if let Some(value) = rule.style.color {
                    computed.color = Some(value);
                }
                if let Some(value) = rule.style.background_color {
                    computed.background_color = Some(value);
                }
            }
        }
        computed
    }
}

fn parse_css_color(value: &str) -> Result<[u8; 4], CssError> {
    let (red, green, blue) = if let Some(hex) = value.strip_prefix('#') {
        match hex.len() {
            3 => {
                let bytes = hex.as_bytes();
                let red = parse_hex_digit(bytes[0])?.saturating_mul(17);
                let green = parse_hex_digit(bytes[1])?.saturating_mul(17);
                let blue = parse_hex_digit(bytes[2])?.saturating_mul(17);
                (red, green, blue)
            }
            6 => {
                let bytes = hex.as_bytes();
                (
                    parse_hex_byte(&bytes[0..2])?,
                    parse_hex_byte(&bytes[2..4])?,
                    parse_hex_byte(&bytes[4..6])?,
                )
            }
            _ => return Err(CssError::InvalidValue),
        }
    } else {
        match value {
            "black" => (0, 0, 0),
            "silver" => (192, 192, 192),
            "gray" | "grey" => (128, 128, 128),
            "white" => (255, 255, 255),
            "maroon" => (128, 0, 0),
            "red" => (255, 0, 0),
            "purple" => (128, 0, 128),
            "fuchsia" => (255, 0, 255),
            "green" => (0, 128, 0),
            "lime" => (0, 255, 0),
            "olive" => (128, 128, 0),
            "yellow" => (255, 255, 0),
            "navy" => (0, 0, 128),
            "blue" => (0, 0, 255),
            "teal" => (0, 128, 128),
            "aqua" => (0, 255, 255),
            "orange" => (255, 165, 0),
            "rebeccapurple" => (102, 51, 153),
            _ => return Err(CssError::InvalidValue),
        }
    };
    Ok([red, green, blue, 255])
}

fn parse_hex_digit(byte: u8) -> Result<u8, CssError> {
    match byte {
        b'0'..=b'9' => Ok(byte - b'0'),
        b'a'..=b'f' => Ok(byte - b'a' + 10),
        b'A'..=b'F' => Ok(byte - b'A' + 10),
        _ => Err(CssError::InvalidValue),
    }
}

fn parse_hex_byte(bytes: &[u8]) -> Result<u8, CssError> {
    if bytes.len() != 2 {
        return Err(CssError::InvalidValue);
    }
    Ok(parse_hex_digit(bytes[0])? * 16 + parse_hex_digit(bytes[1])?)
}

fn parse_px(value: &str) -> Result<u32, CssError> {
    let number = value
        .strip_suffix("px")
        .ok_or(CssError::InvalidValue)?
        .trim();
    if number.is_empty() || !number.bytes().all(|byte| byte.is_ascii_digit()) {
        return Err(CssError::InvalidValue);
    }
    let parsed = number.parse::<u32>().map_err(|_| CssError::InvalidValue)?;
    if parsed > HARD_MAX_CSS_PX {
        return Err(CssError::InvalidValue);
    }
    Ok(parsed)
}

/// Computes basic vertical-flow boxes for a ready HARI DOM tree.
pub fn compute_layout(
    document: &DomDocument,
    stylesheet: &StyleSheet,
    options: &LayoutOptions,
) -> Result<LayoutBox, LayoutError> {
    if document.state() != DocumentState::Ready {
        return Err(LayoutError::DocumentNotReady);
    }
    if options.viewport_width == 0 || options.viewport_width > HARD_MAX_CSS_PX {
        return Err(LayoutError::InvalidViewport);
    }
    if options.default_line_height == 0 || options.character_width == 0 {
        return Err(LayoutError::InvalidMetrics);
    }
    document.validate_invariants().map_err(LayoutError::Dom)?;

    let max_boxes = options.max_layout_boxes.min(HARD_MAX_LAYOUT_BOXES);
    let mut generated_boxes = 0usize;
    layout_node(
        document,
        stylesheet,
        document.root(),
        0,
        0,
        options.viewport_width,
        None,
        options,
        max_boxes,
        &mut generated_boxes,
    )?
    .ok_or(LayoutError::Dom(DomError::InvariantViolation))
}

#[allow(clippy::too_many_arguments)]
fn layout_node(
    document: &DomDocument,
    stylesheet: &StyleSheet,
    node_id: NodeId,
    x: i32,
    flow_y: i32,
    containing_width: u32,
    inherited_text_color: Option<[u8; 4]>,
    options: &LayoutOptions,
    max_boxes: usize,
    generated_boxes: &mut usize,
) -> Result<Option<LayoutBox>, LayoutError> {
    let node = document.node(node_id).map_err(LayoutError::Dom)?;
    let kind = node.kind().clone();

    if matches!(&kind, NodeKind::Comment(_) | NodeKind::Doctype(_)) {
        return Ok(None);
    }

    let (box_kind, display, width, height, margin, text_color, background_color) = match kind {
        NodeKind::Document => (
            BoxKind::Document,
            Display::Block,
            containing_width,
            None,
            0,
            inherited_text_color,
            None,
        ),
        NodeKind::Element { tag_name } => {
            let style = stylesheet.style_for(&tag_name);
            let display = style.display.unwrap_or(Display::Block);
            if display == Display::None {
                return Ok(None);
            }
            let margin = style.margin.unwrap_or(0);
            let available_width = containing_width.saturating_sub(margin.saturating_mul(2));
            let text_color = style.color.or(inherited_text_color);
            (
                BoxKind::Element { tag_name },
                display,
                style.width.unwrap_or(available_width).min(available_width),
                style.height,
                margin,
                text_color,
                style.background_color,
            )
        }
        NodeKind::Text(value) => {
            let measured_height = measure_text_height(
                &value,
                containing_width,
                options.character_width,
                options.default_line_height,
            );
            (
                BoxKind::Text(value),
                Display::Block,
                containing_width,
                Some(measured_height),
                0,
                inherited_text_color,
                None,
            )
        }
        NodeKind::Comment(_) | NodeKind::Doctype(_) => return Ok(None),
    };

    *generated_boxes = generated_boxes.saturating_add(1);
    if *generated_boxes > max_boxes {
        return Err(LayoutError::BoxLimitExceeded);
    }

    let box_x = x.saturating_add(margin.min(i32::MAX as u32) as i32);
    let box_y = flow_y.saturating_add(margin.min(i32::MAX as u32) as i32);
    let content_width = width.max(1);
    let mut children = Vec::new();
    let mut cursor_y = box_y;
    let child_ids = document
        .children(node_id)
        .map_err(LayoutError::Dom)?
        .to_vec();

    for child_id in child_ids {
        if let Some(child) = layout_node(
            document,
            stylesheet,
            child_id,
            box_x,
            cursor_y,
            content_width,
            text_color,
            options,
            max_boxes,
            generated_boxes,
        )? {
            cursor_y = cursor_y.saturating_add(child.flow_advance.min(i32::MAX as u32) as i32);
            children.push(child);
        }
    }

    let natural_height = cursor_y.saturating_sub(box_y).max(0) as u32;
    let box_height = height.unwrap_or(natural_height);
    let flow_advance = box_height.saturating_add(margin.saturating_mul(2));

    Ok(Some(LayoutBox {
        node_id,
        kind: box_kind,
        display,
        rect: Rect {
            x: box_x,
            y: box_y,
            width,
            height: box_height,
        },
        margin,
        text_color,
        background_color,
        children,
        flow_advance,
    }))
}

/// Estimate line count using fixed-width metrics. Real font shaping is a later stage.
fn measure_text_height(value: &str, width: u32, character_width: u32, line_height: u32) -> u32 {
    let columns = (width / character_width).max(1) as usize;
    let lines = value
        .split('\n')
        .map(|line| {
            let chars = line.chars().count();
            chars.max(1).div_ceil(columns)
        })
        .sum::<usize>()
        .max(1);
    (lines.min((u32::MAX / line_height) as usize) as u32) * line_height
}

#[cfg(test)]
mod tests {
    use super::{
        BoxKind, CssError, Display, LayoutError, LayoutOptions, Rect, StyleSheet, compute_layout,
    };
    use hari_engine_dom::{DomDocument, DomLimits, NodeId};

    fn fixture() -> DomDocument {
        DomDocument::from_html(
            b"<html><body><h1>Hello</h1><p>World</p></body></html>",
            &DomLimits::default(),
        )
        .unwrap()
    }

    fn find_tag<'a>(layout: &'a super::LayoutBox, name: &str) -> Option<&'a super::LayoutBox> {
        if matches!(&layout.kind, BoxKind::Element { tag_name } if tag_name == name) {
            return Some(layout);
        }
        layout
            .children
            .iter()
            .find_map(|child| find_tag(child, name))
    }

    #[test]
    fn lays_out_basic_document_in_vertical_block_flow() {
        let document = fixture();
        let layout = compute_layout(
            &document,
            &StyleSheet::default(),
            &LayoutOptions {
                viewport_width: 320,
                ..LayoutOptions::default()
            },
        )
        .unwrap();
        assert_eq!(layout.node_id, document.root());
        assert_eq!(layout.rect.width, 320);
        let body = find_tag(&layout, "body").unwrap();
        let heading = find_tag(&layout, "h1").unwrap();
        let paragraph = find_tag(&layout, "p").unwrap();
        assert_eq!(heading.rect.y, body.rect.y);
        assert_eq!(
            paragraph.rect.y,
            heading.rect.y + heading.rect.height as i32
        );
        assert!(layout.rect.height as i32 >= paragraph.rect.y + paragraph.rect.height as i32);
    }

    #[test]
    fn applies_tag_rules_for_width_height_and_margin() {
        let document = fixture();
        let stylesheet =
            StyleSheet::parse("body { width: 280px; height: 90px; margin: 4px; }").unwrap();
        let layout = compute_layout(
            &document,
            &stylesheet,
            &LayoutOptions {
                viewport_width: 320,
                ..LayoutOptions::default()
            },
        )
        .unwrap();
        let body = find_tag(&layout, "body").unwrap();
        assert_eq!(
            body.rect,
            Rect {
                x: 4,
                y: 4,
                width: 280,
                height: 90
            }
        );
    }

    #[test]
    fn cascades_basic_text_colors_and_keeps_backgrounds_non_inherited() {
        let document =
            DomDocument::from_html(b"<body><p>HARI</p></body>", &DomLimits::default()).unwrap();
        let stylesheet = StyleSheet::parse(
            "body { color: #336699; background-color: #eee; } p { background-color: orange; }",
        )
        .unwrap();
        let layout = compute_layout(&document, &stylesheet, &LayoutOptions::default()).unwrap();
        let body = find_tag(&layout, "body").unwrap();
        let paragraph = find_tag(&layout, "p").unwrap();

        assert_eq!(body.text_color, Some([0x33, 0x66, 0x99, 255]));
        assert_eq!(body.background_color, Some([0xEE, 0xEE, 0xEE, 255]));
        assert_eq!(paragraph.text_color, Some([0x33, 0x66, 0x99, 255]));
        assert_eq!(paragraph.background_color, Some([255, 165, 0, 255]));
        assert_eq!(
            paragraph.children[0].text_color,
            Some([0x33, 0x66, 0x99, 255])
        );
        assert_eq!(paragraph.children[0].background_color, None);
    }

    #[test]
    fn rejects_invalid_css_colors_instead_of_ignoring_them() {
        assert_eq!(
            StyleSheet::parse("p { color: #12ab; }"),
            Err(CssError::InvalidValue)
        );
        assert_eq!(
            StyleSheet::parse("p { background-color: url(image.png); }"),
            Err(CssError::InvalidValue)
        );
    }

    #[test]
    fn default_hidden_html_elements_are_excluded_from_layout() {
        let document = DomDocument::from_html(
            br#"<html><head><title>head title</title></head><body><title>body title</title><style>p { color: red; }</style><script>alert(1);</script><meta name="x"><link rel="stylesheet"><base href="/"><p>VISIBLE</p></body></html>"#,
            &DomLimits::default(),
        )
        .unwrap();
        let layout = compute_layout(&document, &StyleSheet::default(), &LayoutOptions::default())
            .unwrap();

        for tag in ["head", "title", "style", "script", "meta", "link", "base"] {
            assert!(
                find_tag(&layout, tag).is_none(),
                "{tag} should not create a visible layout box"
            );
        }
        assert!(find_tag(&layout, "p").is_some());
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
        let document = DomDocument::from_html(b"<p>abcde</p>", &DomLimits::default()).unwrap();
        let layout = compute_layout(
            &document,
            &StyleSheet::default(),
            &LayoutOptions {
                viewport_width: 16,
                character_width: 8,
                default_line_height: 18,
                ..LayoutOptions::default()
            },
        )
        .unwrap();
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
    fn rejects_unsupported_pseudo_class_selectors() {
        assert_eq!(
            StyleSheet::parse("p:hover { display: none; }"),
            Err(CssError::UnsupportedSelector)
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
            compute_layout(
                &document,
                &StyleSheet::default(),
                &LayoutOptions {
                    viewport_width: 0,
                    ..LayoutOptions::default()
                }
            ),
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
        let document =
            DomDocument::from_html(b"<div><p>x</p></div>", &DomLimits::default()).unwrap();
        assert_eq!(
            compute_layout(
                &document,
                &StyleSheet::default(),
                &LayoutOptions {
                    max_layout_boxes: 2,
                    ..LayoutOptions::default()
                }
            ),
            Err(LayoutError::BoxLimitExceeded)
        );
    }

    #[test]
    fn box_nodes_retain_source_dom_ids() {
        let document = fixture();
        let layout =
            compute_layout(&document, &StyleSheet::default(), &LayoutOptions::default()).unwrap();
        assert_eq!(layout.node_id, NodeId(0));
        assert!(matches!(layout.kind, BoxKind::Document));
        assert!(matches!(layout.display, Display::Block));
    }
}

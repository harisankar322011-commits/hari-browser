//! Initial CSS subset and deterministic flow layout for HARI Engine.
//!
//! This module is a teaching/prototype pipeline, not a standards-compliant CSS
//! cascade or layout engine. Inline runs use fixed character-width metrics; it
//! does not paint pixels, shape fonts, load fonts, or collapse whitespace. Its
//! bounded selector subset supports type, universal, class, ID, compound,
//! descendant, and child selectors, but not sibling combinators, attribute selectors, or pseudo-classes.

use hari_engine_dom::{DocumentState, DomDocument, DomError, DomNode, NodeId, NodeKind};

const HARD_MAX_LAYOUT_BOXES: usize = 100_000;
const HARD_MAX_CSS_BYTES: usize = 256 * 1024;
const HARD_MAX_CSS_RULES: usize = 128;
const HARD_MAX_CSS_DECLARATIONS_PER_RULE: usize = 64;
const HARD_MAX_CSS_SELECTOR_BYTES: usize = 1024;
const HARD_MAX_CSS_SELECTOR_IDENTIFIER_BYTES: usize = 128;
const HARD_MAX_CSS_SELECTOR_PARTS: usize = 16;
const HARD_MAX_CSS_SELECTOR_COMPOUNDS: usize = 16;
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
    Inline,
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
struct CompoundSelector {
    tag_name: Option<String>,
    id: Option<String>,
    classes: Vec<String>,
}

impl CompoundSelector {
    /// Parses one compound selector, such as `p.notice#main` or `*.feature`.
    fn parse(source: &str) -> Result<Self, CssError> {
        if source.is_empty() || source.len() > HARD_MAX_CSS_SELECTOR_BYTES {
            return Err(CssError::UnsupportedSelector);
        }

        let bytes = source.as_bytes();
        let mut index = 0usize;
        let mut tag_name = None;
        let mut id = None;
        let mut classes = Vec::new();

        if bytes[0] == b'*' {
            index = 1;
        } else if bytes[0] != b'.' && bytes[0] != b'#' {
            let start = index;
            while index < bytes.len() && is_selector_identifier_byte(bytes[index]) {
                index += 1;
            }
            if start == index {
                return Err(CssError::UnsupportedSelector);
            }
            tag_name = Some(source[start..index].to_ascii_lowercase());
        }

        while index < bytes.len() {
            let marker = bytes[index];
            if marker != b'.' && marker != b'#' {
                return Err(CssError::UnsupportedSelector);
            }
            index += 1;
            let start = index;
            while index < bytes.len() && is_selector_identifier_byte(bytes[index]) {
                index += 1;
            }
            let identifier_length = index - start;
            if identifier_length == 0 || identifier_length > HARD_MAX_CSS_SELECTOR_IDENTIFIER_BYTES
            {
                return Err(CssError::UnsupportedSelector);
            }

            let identifier = source[start..index].to_owned();
            match marker {
                b'#' => {
                    if id.is_some() {
                        return Err(CssError::UnsupportedSelector);
                    }
                    id = Some(identifier);
                }
                b'.' => classes.push(identifier),
                _ => return Err(CssError::UnsupportedSelector),
            }

            let id_parts = if id.is_some() { 1 } else { 0 };
            let type_parts = if tag_name.is_some() { 1 } else { 0 };
            if classes.len() + id_parts + type_parts > HARD_MAX_CSS_SELECTOR_PARTS {
                return Err(CssError::UnsupportedSelector);
            }
        }

        if tag_name.is_none() && id.is_none() && classes.is_empty() && bytes[0] != b'*' {
            return Err(CssError::UnsupportedSelector);
        }

        Ok(Self {
            tag_name,
            id,
            classes,
        })
    }

    fn matches(&self, tag_name: &str, node: &DomNode) -> bool {
        if self
            .tag_name
            .as_deref()
            .is_some_and(|expected| !expected.eq_ignore_ascii_case(tag_name))
        {
            return false;
        }

        if self
            .id
            .as_deref()
            .is_some_and(|expected| node.attribute("id") != Some(expected))
        {
            return false;
        }

        let class_attribute = node.attribute("class").unwrap_or_default();
        self.classes.iter().all(|expected| {
            class_attribute
                .split_ascii_whitespace()
                .any(|actual| actual == expected)
        })
    }

    /// CSS specificity tuple: (IDs, classes, type selectors).
    fn specificity(&self) -> (u8, u8, u8) {
        (
            u8::from(self.id.is_some()),
            self.classes.len().min(u8::MAX as usize) as u8,
            u8::from(self.tag_name.is_some()),
        )
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum SelectorCombinator {
    Descendant,
    Child,
}

#[derive(Debug, Clone, PartialEq, Eq)]
struct Selector {
    compounds: Vec<CompoundSelector>,
    /// Each combinator joins compound[i] to compound[i + 1].
    combinators: Vec<SelectorCombinator>,
}

impl Selector {
    /// Parses a bounded chain of compound selectors joined by descendant or child combinators.
    /// Attribute selectors, pseudo-classes, sibling combinators, and selector lists are unsupported.
    fn parse(source: &str) -> Result<Self, CssError> {
        if source.is_empty() || source.len() > HARD_MAX_CSS_SELECTOR_BYTES {
            return Err(CssError::UnsupportedSelector);
        }

        let bytes = source.as_bytes();
        let mut index = 0usize;
        let mut compounds = Vec::new();
        let mut combinators = Vec::new();

        while index < bytes.len() {
            let whitespace_start = index;
            while index < bytes.len() && bytes[index].is_ascii_whitespace() {
                index += 1;
            }
            let had_whitespace = index > whitespace_start;
            if index >= bytes.len() {
                break;
            }

            if compounds.is_empty() {
                if bytes[index] == b'>' {
                    return Err(CssError::UnsupportedSelector);
                }
            } else {
                let combinator = if bytes[index] == b'>' {
                    index += 1;
                    while index < bytes.len() && bytes[index].is_ascii_whitespace() {
                        index += 1;
                    }
                    SelectorCombinator::Child
                } else if had_whitespace {
                    SelectorCombinator::Descendant
                } else {
                    return Err(CssError::UnsupportedSelector);
                };

                if index >= bytes.len() || bytes[index] == b'>' {
                    return Err(CssError::UnsupportedSelector);
                }
                combinators.push(combinator);
            }

            let compound_start = index;
            while index < bytes.len()
                && !bytes[index].is_ascii_whitespace()
                && bytes[index] != b'>'
            {
                index += 1;
            }
            if compound_start == index {
                return Err(CssError::UnsupportedSelector);
            }

            compounds.push(CompoundSelector::parse(&source[compound_start..index])?);
            if compounds.len() > HARD_MAX_CSS_SELECTOR_COMPOUNDS {
                return Err(CssError::UnsupportedSelector);
            }
        }

        if compounds.is_empty() || combinators.len() + 1 != compounds.len() {
            return Err(CssError::UnsupportedSelector);
        }

        Ok(Self {
            compounds,
            combinators,
        })
    }

    fn matches(&self, document: &DomDocument, node_id: NodeId) -> bool {
        self.matches_from(document, node_id, self.compounds.len() - 1)
    }

    fn matches_from(&self, document: &DomDocument, node_id: NodeId, index: usize) -> bool {
        let Ok(node) = document.node(node_id) else {
            return false;
        };
        let Some(tag_name) = node.tag_name() else {
            return false;
        };
        if !self.compounds[index].matches(tag_name, node) {
            return false;
        }
        if index == 0 {
            return true;
        }

        let mut ancestor = node.parent();
        match self.combinators[index - 1] {
            SelectorCombinator::Child => ancestor
                .is_some_and(|parent_id| self.matches_from(document, parent_id, index - 1)),
            SelectorCombinator::Descendant => {
                while let Some(parent_id) = ancestor {
                    if self.matches_from(document, parent_id, index - 1) {
                        return true;
                    }
                    ancestor = document
                        .node(parent_id)
                        .ok()
                        .and_then(|parent| parent.parent());
                }
                false
            }
        }
    }

    /// Specificity is the component-wise sum across the complete selector chain.
    fn specificity(&self) -> (u8, u8, u8) {
        self.compounds.iter().fold((0, 0, 0), |total, compound| {
            let specificity = compound.specificity();
            (
                total.0.saturating_add(specificity.0),
                total.1.saturating_add(specificity.1),
                total.2.saturating_add(specificity.2),
            )
        })
    }
}

fn is_selector_identifier_byte(byte: u8) -> bool {
    byte.is_ascii_alphanumeric() || matches!(byte, b'-' | b'_')
}

#[derive(Debug, Clone, PartialEq, Eq)]
struct StyleRule {
    selector: Selector,
    style: ElementStyle,
}

#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct StyleSheet {
    rules: Vec<StyleRule>,
}

impl StyleSheet {
    /// Parses bounded compound/descendant/child selectors and a small set of pixel/color declarations.
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
            let selector = Selector::parse(remaining[..open].trim())?;

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
                            "inline" => Display::Inline,
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

            rules.push(StyleRule { selector, style });
            remaining = remaining[close + 1..].trim();
        }

        Ok(Self { rules })
    }

    fn style_for(
        &self,
        document: &DomDocument,
        node_id: NodeId,
        tag_name: &str,
        node: &DomNode,
    ) -> ElementStyle {
        // Basic user-agent defaults for elements that do not generate visible boxes.
        // The small author stylesheet subset may override these defaults, just as
        // author CSS can override ordinary user-agent declarations in a browser.
        let default_display = if matches!(
            tag_name,
            "base" | "head" | "link" | "meta" | "script" | "style" | "template" | "title"
        ) {
            Display::None
        } else if matches!(
            tag_name,
            "a" | "abbr"
                | "b"
                | "bdi"
                | "bdo"
                | "cite"
                | "code"
                | "data"
                | "em"
                | "i"
                | "label"
                | "mark"
                | "q"
                | "s"
                | "small"
                | "span"
                | "strong"
                | "sub"
                | "sup"
                | "time"
                | "u"
                | "var"
        ) {
            Display::Inline
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

        // Apply lower-specificity rules first and source order within equal
        // specificity, so later declarations win only when CSS says they should.
        let mut matching: Vec<((u8, u8, u8), usize, &StyleRule)> = self
            .rules
            .iter()
            .enumerate()
            .filter_map(|(order, rule)| {
                rule.selector.matches(document, node_id).then_some((
                    rule.selector.specificity(),
                    order,
                    rule,
                ))
            })
            .collect();
        matching.sort_by_key(|(specificity, order, _)| (*specificity, *order));

        for (_, _, rule) in matching {
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
            let style = stylesheet.style_for(document, node_id, &tag_name, node);
            let display = style.display.unwrap_or(Display::Block);
            if display == Display::None {
                return Ok(None);
            }
            let margin = style.margin.unwrap_or(0);
            let available_width = containing_width.saturating_sub(margin.saturating_mul(2));
            let intrinsic_width = if display == Display::Inline {
                measure_inline_content_width(
                    document,
                    stylesheet,
                    node_id,
                    options.character_width,
                )?
            } else {
                available_width
            };
            let text_color = style.color.or(inherited_text_color);
            (
                BoxKind::Element { tag_name },
                display,
                style.width.unwrap_or(intrinsic_width).min(available_width),
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
            let measured_width =
                measure_text_width(&value, containing_width, options.character_width);
            (
                BoxKind::Text(value),
                Display::Inline,
                measured_width,
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
    let mut inline_x = 0u32;
    let mut inline_line_height = options.default_line_height;
    let child_ids = document
        .children(node_id)
        .map_err(LayoutError::Dom)?
        .to_vec();

    for child_id in child_ids {
        if is_inline_node(document, stylesheet, child_id)? {
            let intrinsic_width = measure_inline_content_width(
                document,
                stylesheet,
                child_id,
                options.character_width,
            )?;
            let remaining_width = content_width.saturating_sub(inline_x);
            if inline_x > 0 && intrinsic_width > remaining_width {
                cursor_y = cursor_y.saturating_add(inline_line_height.min(i32::MAX as u32) as i32);
                inline_x = 0;
                inline_line_height = options.default_line_height;
            }

            let child_x = box_x.saturating_add(inline_x.min(i32::MAX as u32) as i32);
            let child_width = content_width.saturating_sub(inline_x).max(1);
            if let Some(child) = layout_node(
                document,
                stylesheet,
                child_id,
                child_x,
                cursor_y,
                child_width,
                text_color,
                options,
                max_boxes,
                generated_boxes,
            )? {
                inline_x = inline_x.saturating_add(child.rect.width);
                inline_line_height = inline_line_height.max(child.rect.height);
                children.push(child);
            }
        } else {
            if inline_x > 0 {
                cursor_y = cursor_y.saturating_add(inline_line_height.min(i32::MAX as u32) as i32);
                inline_x = 0;
                inline_line_height = options.default_line_height;
            }

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
    }

    let natural_height = (cursor_y.saturating_sub(box_y).max(0) as u32)
        .saturating_add(if inline_x > 0 { inline_line_height } else { 0 });
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

fn is_inline_node(
    document: &DomDocument,
    stylesheet: &StyleSheet,
    node_id: NodeId,
) -> Result<bool, LayoutError> {
    let node = document.node(node_id).map_err(LayoutError::Dom)?;
    Ok(match node.kind() {
        NodeKind::Text(_) => true,
        NodeKind::Element { tag_name } => {
            stylesheet.style_for(document, node_id, tag_name, node).display == Some(Display::Inline)
        }
        NodeKind::Document | NodeKind::Comment(_) | NodeKind::Doctype(_) => false,
    })
}

/// Measures visible text descendants for the prototype's fixed-width inline model.
fn measure_inline_content_width(
    document: &DomDocument,
    stylesheet: &StyleSheet,
    node_id: NodeId,
    character_width: u32,
) -> Result<u32, LayoutError> {
    let node = document.node(node_id).map_err(LayoutError::Dom)?;
    match node.kind() {
        NodeKind::Text(value) => Ok(measure_text_width(value, HARD_MAX_CSS_PX, character_width)),
        NodeKind::Element { tag_name }
            if stylesheet.style_for(document, node_id, tag_name, node).display == Some(Display::None) =>
        {
            Ok(0)
        }
        NodeKind::Element { .. } | NodeKind::Document => {
            let mut width = 0u32;
            let child_ids = document
                .children(node_id)
                .map_err(LayoutError::Dom)?
                .to_vec();
            for child_id in child_ids {
                width = width.saturating_add(measure_inline_content_width(
                    document,
                    stylesheet,
                    child_id,
                    character_width,
                )?);
                if width >= HARD_MAX_CSS_PX {
                    return Ok(HARD_MAX_CSS_PX);
                }
            }
            Ok(width)
        }
        NodeKind::Comment(_) | NodeKind::Doctype(_) => Ok(0),
    }
}

/// Measure the longest line in fixed-width character metrics. Real font shaping is a later stage.
fn measure_text_width(value: &str, width: u32, character_width: u32) -> u32 {
    let max_columns = (width / character_width).max(1) as usize;
    let columns = value
        .split('\n')
        .map(|line| line.chars().count().min(max_columns))
        .max()
        .unwrap_or(0);
    (columns as u32).saturating_mul(character_width).min(width)
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
        BoxKind, CssError, Display, HARD_MAX_CSS_SELECTOR_IDENTIFIER_BYTES,
        HARD_MAX_CSS_SELECTOR_PARTS, LayoutError, LayoutOptions, Rect, Selector, StyleSheet,
        compute_layout,
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

    fn find_text<'a>(layout: &'a super::LayoutBox, value: &str) -> Option<&'a super::LayoutBox> {
        if matches!(&layout.kind, BoxKind::Text(text) if text == value) {
            return Some(layout);
        }
        layout
            .children
            .iter()
            .find_map(|child| find_text(child, value))
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
        let layout =
            compute_layout(&document, &StyleSheet::default(), &LayoutOptions::default()).unwrap();

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
    fn inline_text_and_elements_share_a_line() {
        let document =
            DomDocument::from_html(b"<p>Hi <span>there</span>!</p>", &DomLimits::default())
                .unwrap();
        let stylesheet = StyleSheet::parse("span { display: inline; }").unwrap();
        let layout = compute_layout(
            &document,
            &stylesheet,
            &LayoutOptions {
                viewport_width: 128,
                character_width: 8,
                default_line_height: 18,
                ..LayoutOptions::default()
            },
        )
        .unwrap();

        let paragraph = find_tag(&layout, "p").unwrap();
        let first_text = find_text(&layout, "Hi ").unwrap();
        let span = find_tag(&layout, "span").unwrap();
        let last_text = find_text(&layout, "!").unwrap();

        assert_eq!(first_text.rect.width, 24);
        assert_eq!(span.rect.width, 40);
        assert_eq!(last_text.rect.width, 8);
        assert_eq!(
            span.rect.x,
            first_text.rect.x + first_text.rect.width as i32
        );
        assert_eq!(last_text.rect.x, span.rect.x + span.rect.width as i32);
        assert_eq!(first_text.rect.y, span.rect.y);
        assert_eq!(first_text.rect.y, last_text.rect.y);
        assert_eq!(paragraph.rect.height, 18);
    }

    #[test]
    fn wraps_inline_siblings_to_a_new_line() {
        let document =
            DomDocument::from_html(b"<p>12345<span>abc</span></p>", &DomLimits::default()).unwrap();
        let layout = compute_layout(
            &document,
            &StyleSheet::default(),
            &LayoutOptions {
                viewport_width: 40,
                character_width: 8,
                default_line_height: 18,
                ..LayoutOptions::default()
            },
        )
        .unwrap();

        let paragraph = find_tag(&layout, "p").unwrap();
        let first_text = find_text(&layout, "12345").unwrap();
        let span = find_tag(&layout, "span").unwrap();

        assert_eq!(span.rect.x, paragraph.rect.x);
        assert_eq!(span.rect.y, first_text.rect.y + 18);
        assert_eq!(paragraph.rect.height, 36);
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
    fn accepts_bounded_descendant_and_child_selectors_but_rejects_malformed_chains() {
        assert!(Selector::parse("div > p").is_ok());
        assert!(Selector::parse("main section p").is_ok());
        assert!(Selector::parse("main > section.feature p#target").is_ok());
        for invalid in ["> div", "div >", "div > > p", "div,,p"] {
            assert_eq!(
                Selector::parse(invalid),
                Err(CssError::UnsupportedSelector),
                "selector should be rejected: {invalid}"
            );
        }
        assert_eq!(
            Selector::parse("p:first-child"),
            Err(CssError::UnsupportedSelector)
        );
        assert_eq!(
            Selector::parse(&format!(
                ".{}",
                "a".repeat(HARD_MAX_CSS_SELECTOR_IDENTIFIER_BYTES + 1)
            )),
            Err(CssError::UnsupportedSelector)
        );
        assert_eq!(
            Selector::parse(&format!("div{}", ".x".repeat(HARD_MAX_CSS_SELECTOR_PARTS))),
            Err(CssError::UnsupportedSelector)
        );
    }

    #[test]
    fn universal_selector_matches_any_element() {
        let document =
            DomDocument::from_html(b"<main><p>HARI</p></main>", &DomLimits::default()).unwrap();
        let stylesheet = StyleSheet::parse("* { color: blue; }").unwrap();
        let layout = compute_layout(&document, &stylesheet, &LayoutOptions::default()).unwrap();

        assert_eq!(
            find_tag(&layout, "main").unwrap().text_color,
            Some([0, 0, 255, 255])
        );
        assert_eq!(
            find_tag(&layout, "p").unwrap().text_color,
            Some([0, 0, 255, 255])
        );
    }

    #[test]
    fn matches_id_and_class_selectors_on_elements() {
        let document = DomDocument::from_html(
            b"<main id=\"hero\" class=\"shell wide\"><p class=\"notice feature\">HARI</p></main>",
            &DomLimits::default(),
        )
        .unwrap();
        let stylesheet =
            StyleSheet::parse("#hero { width: 240px; } .feature { color: red; }").unwrap();
        let layout = compute_layout(
            &document,
            &stylesheet,
            &LayoutOptions {
                viewport_width: 320,
                ..LayoutOptions::default()
            },
        )
        .unwrap();

        let main = find_tag(&layout, "main").unwrap();
        let paragraph = find_tag(&layout, "p").unwrap();
        assert_eq!(main.rect.width, 240);
        assert_eq!(paragraph.text_color, Some([255, 0, 0, 255]));
    }

    #[test]
    fn selector_specificity_beats_later_lower_specificity_rules() {
        let document = DomDocument::from_html(
            b"<p id=\"message\" class=\"note\">HARI</p>",
            &DomLimits::default(),
        )
        .unwrap();
        let stylesheet = StyleSheet::parse(
            "p.note { color: red; background-color: red; } \
             .note { color: green; } \
             #message { background-color: blue; } \
             p.note { background-color: red; }",
        )
        .unwrap();
        let layout = compute_layout(&document, &stylesheet, &LayoutOptions::default()).unwrap();
        let paragraph = find_tag(&layout, "p").unwrap();

        assert_eq!(paragraph.text_color, Some([255, 0, 0, 255]));
        assert_eq!(paragraph.background_color, Some([0, 0, 255, 255]));
    }

    #[test]
    fn descendant_selectors_match_direct_and_deep_descendants() {
        let document = DomDocument::from_html(
            b"<main><section><p>nested</p></section><p>direct</p></main>",
            &DomLimits::default(),
        )
        .unwrap();
        let stylesheet = StyleSheet::parse("main p { color: red; }").unwrap();
        let layout = compute_layout(&document, &stylesheet, &LayoutOptions::default()).unwrap();

        assert_eq!(
            find_text(&layout, "nested").unwrap().text_color,
            Some([255, 0, 0, 255])
        );
        assert_eq!(
            find_text(&layout, "direct").unwrap().text_color,
            Some([255, 0, 0, 255])
        );
    }

    #[test]
    fn child_selectors_match_only_immediate_element_children() {
        let document = DomDocument::from_html(
            b"<main><section><p>nested</p></section><p>direct</p></main>",
            &DomLimits::default(),
        )
        .unwrap();
        let stylesheet = StyleSheet::parse("main > p { color: blue; }").unwrap();
        let layout = compute_layout(&document, &stylesheet, &LayoutOptions::default()).unwrap();

        assert_eq!(
            find_text(&layout, "direct").unwrap().text_color,
            Some([0, 0, 255, 255])
        );
        assert_eq!(find_text(&layout, "nested").unwrap().text_color, None);
    }

    #[test]
    fn selector_specificity_accumulates_across_combinator_chains() {
        let document = DomDocument::from_html(
            b"<main id=\"hero\"><p>target</p></main>",
            &DomLimits::default(),
        )
        .unwrap();
        let stylesheet =
            StyleSheet::parse("#hero p { color: blue; } main p { color: red; }").unwrap();
        let layout = compute_layout(&document, &stylesheet, &LayoutOptions::default()).unwrap();

        assert_eq!(
            find_text(&layout, "target").unwrap().text_color,
            Some([0, 0, 255, 255])
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

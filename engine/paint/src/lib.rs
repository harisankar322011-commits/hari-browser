//! Deterministic software paint surface for the experimental HARI Engine.
//!
//! The renderer uses a tiny built-in 5x7 bitmap font for ASCII text and draws
//! optional element outlines. It is a proof-of-pipeline renderer, not a browser
//! quality graphics stack: no font shaping, anti-aliasing, images, compositing,
//! CSS colors, transforms, or device scale support is provided.

use hari_engine_dom::{DomDocument, DomError, DomLimits};
use hari_engine_layout::{
    BoxKind, CssError, LayoutBox, LayoutError, LayoutOptions, StyleSheet, compute_layout,
};

const HARD_MAX_PIXELS: usize = 1_048_576;
const HARD_MAX_DIMENSION: u32 = 16_384;
const GLYPH_WIDTH: usize = 5;
const GLYPH_HEIGHT: usize = 7;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PaintError {
    InvalidSurfaceDimensions,
    PixelLimitExceeded,
    InvalidMetrics,
    ConfigurationMismatch,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RenderError {
    Dom(DomError),
    Css(CssError),
    Layout(LayoutError),
    Paint(PaintError),
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct PaintOptions {
    pub width: u32,
    pub height: u32,
    pub character_width: u32,
    pub line_height: u32,
    pub max_pixels: usize,
    pub background: [u8; 4],
    pub text_color: [u8; 4],
    pub outline_color: [u8; 4],
    pub draw_element_outlines: bool,
}

impl Default for PaintOptions {
    fn default() -> Self {
        Self {
            width: 360,
            height: 640,
            character_width: 8,
            line_height: 18,
            max_pixels: HARD_MAX_PIXELS,
            background: [255, 255, 255, 255],
            text_color: [20, 24, 32, 255],
            outline_color: [222, 226, 232, 255],
            draw_element_outlines: true,
        }
    }
}

/// A contiguous, opaque RGBA8888 off-screen surface.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Surface {
    width: u32,
    height: u32,
    pixels: Vec<[u8; 4]>,
    background: [u8; 4],
}

impl Surface {
    pub fn new(
        width: u32,
        height: u32,
        background: [u8; 4],
        max_pixels: usize,
    ) -> Result<Self, PaintError> {
        if width == 0 || height == 0 || width > HARD_MAX_DIMENSION || height > HARD_MAX_DIMENSION {
            return Err(PaintError::InvalidSurfaceDimensions);
        }
        let pixel_count = (width as usize)
            .checked_mul(height as usize)
            .ok_or(PaintError::PixelLimitExceeded)?;
        let effective_budget = max_pixels.clamp(1, HARD_MAX_PIXELS);
        if pixel_count > effective_budget {
            return Err(PaintError::PixelLimitExceeded);
        }

        let mut pixels = Vec::new();
        pixels
            .try_reserve_exact(pixel_count)
            .map_err(|_| PaintError::PixelLimitExceeded)?;
        pixels.resize(pixel_count, background);

        Ok(Self {
            width,
            height,
            pixels,
            background,
        })
    }

    pub fn width(&self) -> u32 {
        self.width
    }

    pub fn height(&self) -> u32 {
        self.height
    }

    pub fn pixels(&self) -> &[[u8; 4]] {
        &self.pixels
    }

    pub fn pixel(&self, x: u32, y: u32) -> Option<[u8; 4]> {
        if x >= self.width || y >= self.height {
            return None;
        }
        self.pixels
            .get((y as usize) * (self.width as usize) + x as usize)
            .copied()
    }

    pub fn non_background_pixel_count(&self) -> usize {
        self.pixels
            .iter()
            .filter(|pixel| **pixel != self.background)
            .count()
    }

    fn set_pixel(&mut self, x: i32, y: i32, color: [u8; 4]) {
        if x < 0 || y < 0 || x as u32 >= self.width || y as u32 >= self.height {
            return;
        }
        let index = (y as usize) * self.width as usize + x as usize;
        if let Some(pixel) = self.pixels.get_mut(index) {
            *pixel = color;
        }
    }
}

/// Paints a computed HARI layout tree to a bounded in-memory RGBA surface.
pub fn paint_layout(layout: &LayoutBox, options: &PaintOptions) -> Result<Surface, PaintError> {
    if options.character_width < GLYPH_WIDTH as u32 + 1 || options.line_height < GLYPH_HEIGHT as u32
    {
        return Err(PaintError::InvalidMetrics);
    }

    let mut surface = Surface::new(
        options.width,
        options.height,
        options.background,
        options.max_pixels,
    )?;
    paint_node(layout, &mut surface, options);
    Ok(surface)
}

/// Runs the current end-to-end prototype: bounded HTML parse, DOM import,
/// small CSS parse, block-flow layout, then deterministic software painting.
pub fn render_html_to_surface(
    html: &[u8],
    css: &str,
    dom_limits: &DomLimits,
    layout_options: &LayoutOptions,
    paint_options: &PaintOptions,
) -> Result<Surface, RenderError> {
    if layout_options.viewport_width != paint_options.width
        || layout_options.character_width != paint_options.character_width
        || layout_options.default_line_height != paint_options.line_height
    {
        return Err(RenderError::Paint(PaintError::ConfigurationMismatch));
    }

    let document = DomDocument::from_html(html, dom_limits).map_err(RenderError::Dom)?;
    let stylesheet = StyleSheet::parse(css).map_err(RenderError::Css)?;
    let layout =
        compute_layout(&document, &stylesheet, layout_options).map_err(RenderError::Layout)?;
    paint_layout(&layout, paint_options).map_err(RenderError::Paint)
}

fn paint_node(node: &LayoutBox, surface: &mut Surface, options: &PaintOptions) {
    match &node.kind {
        BoxKind::Document => {}
        BoxKind::Element { .. } => {
            if options.draw_element_outlines {
                draw_outline(
                    surface,
                    node.rect.x,
                    node.rect.y,
                    node.rect.width,
                    node.rect.height,
                    options.outline_color,
                );
            }
        }
        BoxKind::Text(text) => draw_text(surface, text, node, options),
    }

    for child in &node.children {
        paint_node(child, surface, options);
    }
}

fn draw_outline(surface: &mut Surface, x: i32, y: i32, width: u32, height: u32, color: [u8; 4]) {
    if width == 0 || height == 0 {
        return;
    }
    for dx in 0..width {
        let px = x.saturating_add(dx.min(i32::MAX as u32) as i32);
        surface.set_pixel(px, y, color);
        surface.set_pixel(
            px,
            y.saturating_add(height.saturating_sub(1).min(i32::MAX as u32) as i32),
            color,
        );
    }
    for dy in 0..height {
        let py = y.saturating_add(dy.min(i32::MAX as u32) as i32);
        surface.set_pixel(x, py, color);
        surface.set_pixel(
            x.saturating_add(width.saturating_sub(1).min(i32::MAX as u32) as i32),
            py,
            color,
        );
    }
}

fn draw_text(surface: &mut Surface, text: &str, node: &LayoutBox, options: &PaintOptions) {
    let columns = (node.rect.width / options.character_width).max(1) as usize;
    let mut line = 0usize;
    let mut column = 0usize;
    for character in text.chars() {
        if character == '\r' {
            continue;
        }
        if character == '\n' {
            line = line.saturating_add(1);
            column = 0;
            continue;
        }
        if column >= columns {
            line = line.saturating_add(1);
            column = 0;
        }

        let line_offset = (line as u32).saturating_mul(options.line_height);
        if line_offset >= node.rect.height {
            break;
        }
        let origin_x = node.rect.x.saturating_add(
            (column as u32)
                .saturating_mul(options.character_width)
                .min(i32::MAX as u32) as i32,
        );
        let origin_y = node
            .rect
            .y
            .saturating_add(line_offset.min(i32::MAX as u32) as i32);
        draw_glyph(surface, origin_x, origin_y, character, options.text_color);
        column = column.saturating_add(1);
    }
}

fn draw_glyph(
    surface: &mut Surface,
    origin_x: i32,
    origin_y: i32,
    character: char,
    color: [u8; 4],
) {
    let rows = glyph_rows(character.to_ascii_uppercase());
    for (row_index, bits) in rows.iter().enumerate() {
        for column in 0..GLYPH_WIDTH {
            if *bits & (1 << (GLYPH_WIDTH - 1 - column)) != 0 {
                surface.set_pixel(
                    origin_x.saturating_add(column as i32),
                    origin_y.saturating_add(row_index as i32),
                    color,
                );
            }
        }
    }
}

fn glyph_rows(character: char) -> [u8; GLYPH_HEIGHT] {
    match character {
        'A' => [14, 17, 17, 31, 17, 17, 17],
        'B' => [30, 17, 17, 30, 17, 17, 30],
        'C' => [14, 17, 16, 16, 16, 17, 14],
        'D' => [30, 17, 17, 17, 17, 17, 30],
        'E' => [31, 16, 16, 30, 16, 16, 31],
        'F' => [31, 16, 16, 30, 16, 16, 16],
        'G' => [14, 17, 16, 23, 17, 17, 15],
        'H' => [17, 17, 17, 31, 17, 17, 17],
        'I' => [31, 4, 4, 4, 4, 4, 31],
        'J' => [7, 2, 2, 2, 18, 18, 12],
        'K' => [17, 18, 20, 24, 20, 18, 17],
        'L' => [16, 16, 16, 16, 16, 16, 31],
        'M' => [17, 27, 21, 21, 17, 17, 17],
        'N' => [17, 25, 21, 19, 17, 17, 17],
        'O' => [14, 17, 17, 17, 17, 17, 14],
        'P' => [30, 17, 17, 30, 16, 16, 16],
        'Q' => [14, 17, 17, 17, 21, 18, 13],
        'R' => [30, 17, 17, 30, 20, 18, 17],
        'S' => [15, 16, 16, 14, 1, 1, 30],
        'T' => [31, 4, 4, 4, 4, 4, 4],
        'U' => [17, 17, 17, 17, 17, 17, 14],
        'V' => [17, 17, 17, 17, 17, 10, 4],
        'W' => [17, 17, 17, 21, 21, 21, 10],
        'X' => [17, 17, 10, 4, 10, 17, 17],
        'Y' => [17, 17, 10, 4, 4, 4, 4],
        'Z' => [31, 1, 2, 4, 8, 16, 31],
        '0' => [14, 17, 19, 21, 25, 17, 14],
        '1' => [4, 12, 4, 4, 4, 4, 14],
        '2' => [14, 17, 1, 2, 4, 8, 31],
        '3' => [30, 1, 1, 14, 1, 1, 30],
        '4' => [2, 6, 10, 18, 31, 2, 2],
        '5' => [31, 16, 16, 30, 1, 1, 30],
        '6' => [14, 16, 16, 30, 17, 17, 14],
        '7' => [31, 1, 2, 4, 8, 8, 8],
        '8' => [14, 17, 17, 14, 17, 17, 14],
        '9' => [14, 17, 17, 15, 1, 1, 14],
        '-' => [0, 0, 0, 31, 0, 0, 0],
        '_' => [0, 0, 0, 0, 0, 0, 31],
        '.' => [0, 0, 0, 0, 0, 12, 12],
        ',' => [0, 0, 0, 0, 4, 4, 8],
        ':' => [0, 4, 4, 0, 4, 4, 0],
        ';' => [0, 4, 4, 0, 4, 4, 8],
        '!' => [4, 4, 4, 4, 4, 0, 4],
        '?' => [14, 17, 1, 2, 4, 0, 4],
        '\'' => [4, 4, 2, 0, 0, 0, 0],
        '"' => [10, 10, 10, 0, 0, 0, 0],
        '/' => [1, 2, 2, 4, 8, 8, 16],
        '(' => [2, 4, 8, 8, 8, 4, 2],
        ')' => [8, 4, 2, 2, 2, 4, 8],
        '+' => [0, 4, 4, 31, 4, 4, 0],
        '=' => [0, 31, 0, 31, 0, 0, 0],
        ' ' => [0; GLYPH_HEIGHT],
        _ => [31, 17, 5, 4, 4, 0, 4],
    }
}

#[cfg(test)]
mod tests {
    use super::{
        PaintError, PaintOptions, RenderError, Surface, paint_layout, render_html_to_surface,
    };
    use hari_engine_dom::DomLimits;
    use hari_engine_layout::{LayoutOptions, StyleSheet};

    #[test]
    fn renders_html_css_dom_layout_pipeline_to_rgba_pixels() {
        let layout_options = LayoutOptions {
            viewport_width: 240,
            character_width: 8,
            default_line_height: 18,
            ..LayoutOptions::default()
        };
        let paint_options = PaintOptions {
            width: 240,
            height: 160,
            character_width: 8,
            line_height: 18,
            ..PaintOptions::default()
        };
        let surface = render_html_to_surface(
            b"<html><body><h1>HARI ENGINE</h1><p>FIRST PAGE</p></body></html>",
            "body { margin: 8px; } h1 { height: 24px; }",
            &DomLimits::default(),
            &layout_options,
            &paint_options,
        )
        .unwrap();

        assert_eq!(surface.width(), 240);
        assert_eq!(surface.height(), 160);
        assert!(surface.pixel(239, 159).is_some());
        assert!(
            surface
                .pixels()
                .iter()
                .any(|pixel| *pixel == paint_options.text_color)
        );
        assert!(
            surface
                .pixels()
                .iter()
                .any(|pixel| *pixel == paint_options.background)
        );
        assert!(surface.non_background_pixel_count() > 100);
    }

    #[test]
    fn painting_is_deterministic_for_the_same_layout_and_options() {
        let layout_options = LayoutOptions {
            viewport_width: 96,
            ..LayoutOptions::default()
        };
        let paint_options = PaintOptions {
            width: 96,
            height: 80,
            ..PaintOptions::default()
        };
        let html = b"<p>HARI</p>";
        let first = render_html_to_surface(
            html,
            "",
            &DomLimits::default(),
            &layout_options,
            &paint_options,
        )
        .unwrap();
        let second = render_html_to_surface(
            html,
            "",
            &DomLimits::default(),
            &layout_options,
            &paint_options,
        )
        .unwrap();
        assert_eq!(first, second);
    }

    #[test]
    fn rejects_zero_surface_dimensions() {
        let layout = hari_engine_layout::compute_layout(
            &hari_engine_dom::DomDocument::from_html(b"<p>x</p>", &DomLimits::default()).unwrap(),
            &StyleSheet::default(),
            &LayoutOptions::default(),
        )
        .unwrap();
        assert_eq!(
            paint_layout(
                &layout,
                &PaintOptions {
                    width: 0,
                    ..PaintOptions::default()
                }
            ),
            Err(PaintError::InvalidSurfaceDimensions)
        );
    }

    #[test]
    fn rejects_surfaces_over_pixel_budget_before_allocating() {
        let layout = hari_engine_layout::compute_layout(
            &hari_engine_dom::DomDocument::from_html(b"<p>x</p>", &DomLimits::default()).unwrap(),
            &StyleSheet::default(),
            &LayoutOptions::default(),
        )
        .unwrap();
        assert_eq!(
            paint_layout(
                &layout,
                &PaintOptions {
                    width: 100,
                    height: 100,
                    max_pixels: 1,
                    ..PaintOptions::default()
                }
            ),
            Err(PaintError::PixelLimitExceeded)
        );
    }

    #[test]
    fn render_pipeline_rejects_inconsistent_viewport_widths() {
        assert_eq!(
            render_html_to_surface(
                b"<p>HARI</p>",
                "",
                &DomLimits::default(),
                &LayoutOptions {
                    viewport_width: 100,
                    ..LayoutOptions::default()
                },
                &PaintOptions {
                    width: 101,
                    ..PaintOptions::default()
                },
            ),
            Err(RenderError::Paint(PaintError::ConfigurationMismatch))
        );
    }

    #[test]
    fn surface_reports_out_of_range_pixels_as_none() {
        let surface = Surface::new(2, 2, [1, 2, 3, 255], 4).unwrap();
        assert_eq!(surface.pixel(0, 0), Some([1, 2, 3, 255]));
        assert_eq!(surface.pixel(2, 0), None);
        assert_eq!(surface.pixel(0, 2), None);
    }
}

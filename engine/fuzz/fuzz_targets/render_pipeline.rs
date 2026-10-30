#![no_main]

//! Bounded end-to-end fuzzing for the experimental HARI render pipeline.
//!
//! This target checks crash resistance and output-budget invariants only. It
//! does not establish HTML/CSS conformance, site safety, or production security.

use hari_engine_dom::DomLimits;
use hari_engine_layout::LayoutOptions;
use hari_engine_paint::{PaintOptions, render_html_to_surface};
use libfuzzer_sys::fuzz_target;

const MAX_FUZZ_INPUT_BYTES: usize = 16 * 1024;
const MAX_DOM_NODES: usize = 256;
const MAX_DOM_DEPTH: usize = 32;
const MAX_LAYOUT_BOXES: usize = 256;
const SURFACE_WIDTH: u32 = 64;
const SURFACE_HEIGHT: u32 = 64;
const MAX_PIXELS: usize = (SURFACE_WIDTH * SURFACE_HEIGHT) as usize;

/// The corpus format is: decimal HTML byte length, newline, HTML bytes, CSS bytes.
/// Returning None for malformed framing keeps arbitrary fuzzer input bounded.
fn split_input(data: &[u8]) -> Option<(&[u8], &[u8])> {
    if data.len() > MAX_FUZZ_INPUT_BYTES {
        return None;
    }

    let separator = data.iter().position(|byte| *byte == b'\n')?;
    if separator == 0 || separator > 5 {
        return None;
    }

    let html_length = std::str::from_utf8(&data[..separator])
        .ok()?
        .parse::<usize>()
        .ok()?;
    let payload = &data[separator + 1..];
    if html_length > payload.len() {
        return None;
    }

    Some(payload.split_at(html_length))
}

fuzz_target!(|data: &[u8]| {
    let Some((html, css_bytes)) = split_input(data) else {
        return;
    };

    // Lossy conversion lets the fuzzer explore malformed CSS byte sequences
    // while retaining the original HTML bytes for the parser's UTF-8 checks.
    let css = String::from_utf8_lossy(css_bytes);
    let dom_limits = DomLimits {
        max_nodes: MAX_DOM_NODES,
        max_depth: MAX_DOM_DEPTH,
    };
    let layout_options = LayoutOptions {
        viewport_width: SURFACE_WIDTH,
        default_line_height: 18,
        character_width: 8,
        max_layout_boxes: MAX_LAYOUT_BOXES,
    };
    let paint_options = PaintOptions {
        width: SURFACE_WIDTH,
        height: SURFACE_HEIGHT,
        character_width: 8,
        line_height: 18,
        max_pixels: MAX_PIXELS,
        draw_element_outlines: false,
        ..PaintOptions::default()
    };

    if let Ok(surface) = render_html_to_surface(
        html,
        &css,
        &dom_limits,
        &layout_options,
        &paint_options,
    ) {
        // A successful render must respect the fixed output budget exactly.
        assert_eq!(surface.width(), SURFACE_WIDTH);
        assert_eq!(surface.height(), SURFACE_HEIGHT);
        assert_eq!(surface.pixels().len(), MAX_PIXELS);
    }
});

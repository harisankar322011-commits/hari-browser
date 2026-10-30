//! Adversarial end-to-end tests for HARI Engine's bounded experimental pipeline.
//!
//! These cases are regression guards for input/resource boundaries, not a claim
//! of HTML conformance or production-grade browser security.

use hari_engine_dom::{DomError, DomLimits};
use hari_engine_layout::{CssError, LayoutOptions};
use hari_engine_paint::{PaintError, PaintOptions, RenderError, render_html_to_surface};

fn options(max_pixels: usize) -> PaintOptions {
    PaintOptions {
        width: 64,
        height: 64,
        character_width: 8,
        line_height: 18,
        max_pixels,
        ..PaintOptions::default()
    }
}

fn render(
    html: &[u8],
    css: &str,
    dom_limits: &DomLimits,
    paint_options: &PaintOptions,
) -> Result<hari_engine_paint::Surface, RenderError> {
    let layout_options = LayoutOptions {
        viewport_width: paint_options.width,
        character_width: paint_options.character_width,
        default_line_height: paint_options.line_height,
        ..LayoutOptions::default()
    };

    render_html_to_surface(html, css, dom_limits, &layout_options, paint_options)
}

#[test]
fn malformed_markup_corpus_finishes_with_bounded_surfaces() {
    let samples = [
        "<div",
        "<p title='unterminated",
        "<!-- unterminated comment",
        "<!doctype html",
        "<script>if (left < right) {",
        "<div><p>before</div></p>",
        "<textarea>unclosed <b>looks like text",
        "</div></span><",
        "<p a=one a=two>duplicate attribute</p>",
        "<ul><li>one<li>two</ul>",
    ];

    for source in samples {
        let surface = render(source.as_bytes(), "", &DomLimits::default(), &options(4096))
            .unwrap_or_else(|error| panic!("bounded recovery failed for {source:?}: {error:?}"));

        assert_eq!(surface.width(), 64, "wrong output width for {source:?}");
        assert_eq!(surface.height(), 64, "wrong output height for {source:?}");
        assert_eq!(
            surface.pixels().len(),
            4096,
            "wrong pixel count for {source:?}"
        );
    }
}

#[test]
fn oversized_document_is_rejected_before_rendering() {
    // The HTML crate's hard maximum is 1 MiB. Exceed it by one byte.
    let html = vec![b'x'; 1_048_577];

    assert!(matches!(
        render(&html, "", &DomLimits::default(), &options(4096)),
        Err(RenderError::Dom(DomError::Html(_)))
    ));
}

#[test]
fn excessive_nesting_is_rejected_by_the_html_boundary() {
    let mut html = "<a>".repeat(300);
    html.push_str(&"</a>".repeat(300));

    assert!(matches!(
        render(html.as_bytes(), "", &DomLimits::default(), &options(4096)),
        Err(RenderError::Dom(DomError::Html(_)))
    ));
}

#[test]
fn dom_node_budget_stops_pipeline_import() {
    let limits = DomLimits {
        max_nodes: 2,
        ..DomLimits::default()
    };

    assert_eq!(
        render(b"<p>text</p>", "", &limits, &options(4096)),
        Err(RenderError::Dom(DomError::NodeLimitExceeded))
    );
}

#[test]
fn unsupported_css_is_rejected_instead_of_silently_ignored() {
    assert_eq!(
        render(
            b"<p>text</p>",
            "p { position: fixed; }",
            &DomLimits::default(),
            &options(4096),
        ),
        Err(RenderError::Css(CssError::UnsupportedProperty))
    );
}

#[test]
fn paint_budget_rejects_large_surface_before_rendering() {
    assert_eq!(
        render(b"<p>text</p>", "", &DomLimits::default(), &options(8)),
        Err(RenderError::Paint(PaintError::PixelLimitExceeded))
    );
}

#[test]
fn successful_parse_is_not_a_safety_verdict() {
    // This fixture asserts only that the prototype follows its documented
    // parse/layout/paint contract. No content-safety conclusion is inferred.
    let surface = render(
        b"<p>untrusted text</p>",
        "",
        &DomLimits::default(),
        &options(4096),
    )
    .unwrap();

    assert!(surface.non_background_pixel_count() > 0);
}

#[test]
fn applies_embedded_style_elements_to_the_rendered_page() {
    let surface = render(
        b"<style>div { background-color: #ff0000; width: 16px; height: 16px; }</style><div>HARI</div>",
        "",
        &DomLimits::default(),
        &options(4096),
    )
    .unwrap();

    assert_eq!(surface.pixel(1, 1), Some([255, 0, 0, 255]));
}

#[test]
fn caller_stylesheet_overrides_embedded_style_rules() {
    let surface = render(
        b"<style>div { background-color: #ff0000; width: 16px; height: 16px; }</style><div>HARI</div>",
        "div { background-color: #0000ff; width: 16px; height: 16px; }",
        &DomLimits::default(),
        &options(4096),
    )
    .unwrap();

    assert_eq!(surface.pixel(1, 1), Some([0, 0, 255, 255]));
}

#[test]
fn ignores_style_elements_declared_with_a_non_css_type() {
    let surface = render(
        b"<style type=application/json>{ this is not CSS }</style><div>HARI</div>",
        "",
        &DomLimits::default(),
        &options(4096),
    )
    .unwrap();

    assert!(surface.non_background_pixel_count() > 0);
}

#[test]
fn does_not_apply_print_only_embedded_styles_to_the_screen_surface() {
    let surface = render(
        b"<style media=print>div { background-color: #ff0000; width: 16px; height: 16px; }</style><div>HARI</div>",
        "",
        &DomLimits::default(),
        &options(4096),
    )
    .unwrap();

    assert_ne!(surface.pixel(1, 1), Some([255, 0, 0, 255]));
}

#[test]
fn applies_embedded_styles_for_a_screen_media_list_entry() {
    let surface = render(
        b"<style media=\"print, screen\">div { background-color: #00ff00; width: 16px; height: 16px; }</style><div>HARI</div>",
        "",
        &DomLimits::default(),
        &options(4096),
    )
    .unwrap();

    assert_eq!(surface.pixel(1, 1), Some([0, 255, 0, 255]));
}

#[test]
fn evaluates_min_width_media_queries_at_the_viewport_boundary() {
    let applies = render(
        b"<style media=\"screen and (min-width: 64px)\">div { background-color: #00ff00; width: 16px; height: 16px; }</style><div>HARI</div>",
        "",
        &DomLimits::default(),
        &options(4096),
    )
    .unwrap();
    let does_not_apply = render(
        b"<style media=\"screen and (min-width: 65px)\">div { background-color: #00ff00; width: 16px; height: 16px; }</style><div>HARI</div>",
        "",
        &DomLimits::default(),
        &options(4096),
    )
    .unwrap();

    assert_eq!(applies.pixel(1, 1), Some([0, 255, 0, 255]));
    assert_ne!(does_not_apply.pixel(1, 1), Some([0, 255, 0, 255]));
}

#[test]
fn evaluates_max_width_media_queries_at_the_viewport_boundary() {
    let applies = render(
        b"<style media=\"screen and (max-width: 64px)\">div { background-color: #00ff00; width: 16px; height: 16px; }</style><div>HARI</div>",
        "",
        &DomLimits::default(),
        &options(4096),
    )
    .unwrap();
    let does_not_apply = render(
        b"<style media=\"screen and (max-width: 63px)\">div { background-color: #00ff00; width: 16px; height: 16px; }</style><div>HARI</div>",
        "",
        &DomLimits::default(),
        &options(4096),
    )
    .unwrap();

    assert_eq!(applies.pixel(1, 1), Some([0, 255, 0, 255]));
    assert_ne!(does_not_apply.pixel(1, 1), Some([0, 255, 0, 255]));
}

#[test]
fn evaluates_bounded_media_query_conjunctions_and_comma_lists() {
    let surface = render(
        b"<style media=\"print, screen and (min-width: 32px) and (max-width: 80px)\">div { background-color: #00ff00; width: 16px; height: 16px; }</style><div>HARI</div>",
        "",
        &DomLimits::default(),
        &options(4096),
    )
    .unwrap();

    assert_eq!(surface.pixel(1, 1), Some([0, 255, 0, 255]));
}


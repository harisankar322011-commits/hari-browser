//! HARI software painter tests are written before the implementation.

#[cfg(test)]
mod tests {
    use super::{
        paint_layout, render_html_to_surface, PaintError, PaintOptions, RenderError, Surface,
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
        assert!(surface.pixels().iter().any(|pixel| *pixel == paint_options.text_color));
        assert!(surface.pixels().iter().any(|pixel| *pixel == paint_options.background));
        assert!(surface.non_background_pixel_count() > 100);
    }

    #[test]
    fn painting_is_deterministic_for_the_same_layout_and_options() {
        let layout_options = LayoutOptions { viewport_width: 96, ..LayoutOptions::default() };
        let paint_options = PaintOptions { width: 96, height: 80, ..PaintOptions::default() };
        let html = b"<p>HARI</p>";
        let first = render_html_to_surface(
            html, "", &DomLimits::default(), &layout_options, &paint_options,
        ).unwrap();
        let second = render_html_to_surface(
            html, "", &DomLimits::default(), &layout_options, &paint_options,
        ).unwrap();
        assert_eq!(first, second);
    }

    #[test]
    fn rejects_zero_surface_dimensions() {
        let layout = hari_engine_layout::compute_layout(
            &hari_engine_dom::DomDocument::from_html(b"<p>x</p>", &DomLimits::default()).unwrap(),
            &StyleSheet::default(),
            &LayoutOptions::default(),
        ).unwrap();
        assert_eq!(
            paint_layout(&layout, &PaintOptions { width: 0, ..PaintOptions::default() }),
            Err(PaintError::InvalidSurfaceDimensions)
        );
    }

    #[test]
    fn rejects_surfaces_over_pixel_budget_before_allocating() {
        let layout = hari_engine_layout::compute_layout(
            &hari_engine_dom::DomDocument::from_html(b"<p>x</p>", &DomLimits::default()).unwrap(),
            &StyleSheet::default(),
            &LayoutOptions::default(),
        ).unwrap();
        assert_eq!(
            paint_layout(&layout, &PaintOptions {
                width: 100,
                height: 100,
                max_pixels: 1,
                ..PaintOptions::default()
            }),
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
                &LayoutOptions { viewport_width: 100, ..LayoutOptions::default() },
                &PaintOptions { width: 101, ..PaintOptions::default() },
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

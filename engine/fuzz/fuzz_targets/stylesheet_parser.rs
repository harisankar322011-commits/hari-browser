#![no_main]

//! Direct, bounded fuzzing for HARI's experimental CSS stylesheet parser.
//!
//! This target checks parser crash resistance only. It does not establish CSS
//! standards conformance or browser security.

use hari_engine_layout::StyleSheet;
use libfuzzer_sys::fuzz_target;

const MAX_FUZZ_INPUT_BYTES: usize = 16 * 1024;

fuzz_target!(|data: &[u8]| {
    if data.len() > MAX_FUZZ_INPUT_BYTES {
        return;
    }

    // Lossy conversion preserves arbitrary-byte exploration while giving the
    // UTF-8 parser API a bounded string. Each context exposes a different
    // parser path without needing a complete valid stylesheet in the input.
    let source = String::from_utf8_lossy(data);

    let _ = StyleSheet::parse(&source);

    let declaration_context = format!("p {{ {source} }}");
    let _ = StyleSheet::parse(&declaration_context);

    let selector_context = format!("{source} {{ color: red; }}");
    let _ = StyleSheet::parse(&selector_context);

    let value_context = format!("p {{ color: {source}; width: 1px; }}");
    let _ = StyleSheet::parse(&value_context);
});

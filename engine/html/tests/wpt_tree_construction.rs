//! Deterministic v1 conformance harness for a curated WPT HTML tree-construction corpus.
//!
//! This compares only document-mode cases representable by HARI's current tree model.
//! Fragment parsing, scripting modes, parse-error diagnostics, foreign namespaces,
//! template contents, processing instructions, and unsupported wrapper synthesis are
//! reported as unsupported rather than silently normalized.

use std::collections::{BTreeMap, HashSet};

use hari_engine_html::{Attribute, HtmlLimits, Node, NodeKind, parse_document};

const WPT_REVISION: &str = "b4cd3d853150fb8e704a56f0099d6fbff2b9550f";
// The first v1.2 audit exposed these expected-error evaluation gaps. They are
// reported separately and never counted as passes; new cases must not increase
// this pinned baseline without an explicit review.
const MAX_EXPECTED_ERROR_EVALUATION_FAILURES: usize = 227;
const BUNDLES: &[&str] = &[
    include_str!("fixtures/wpt/part-01.bundle"),
    include_str!("fixtures/wpt/part-02.bundle"),
    include_str!("fixtures/wpt/part-03.bundle"),
    include_str!("fixtures/wpt/part-04.bundle"),
    include_str!("fixtures/wpt/part-05.bundle"),
];

#[derive(Debug)]
struct Fixture {
    name: String,
    source: String,
}

#[derive(Debug)]
struct TestCase {
    description: String,
    input: String,
    has_parse_errors: bool,
    has_new_errors: bool,
    fragment_context: Option<String>,
    fragment_output: bool,
    scripting_mode: Option<bool>,
    expected_tree: Vec<String>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum ComparisonMode {
    ExactTree,
    BodyProjection,
}

#[derive(Debug, Clone, PartialEq, Eq)]
enum DumpNode {
    Doctype(String),
    Comment(String),
    Element {
        tag_name: String,
        attributes: Vec<(String, String)>,
        children: Vec<DumpNode>,
    },
    Text(String),
}

#[derive(Debug)]
enum DumpError {
    Unsupported(&'static str),
    Invalid(String),
}

#[derive(Debug)]
enum CaseResult {
    Passed(ComparisonMode),
    Unsupported(&'static str),
    Invalid(String),
    Failed(String),
}

fn load_fixtures() -> Vec<Fixture> {
    let mut fixtures = Vec::new();

    for bundle in BUNDLES {
        for entry in bundle.split("@@END-WPT-FIXTURE\n") {
            if entry.is_empty() {
                continue;
            }

            let (header, source) = entry
                .split_once('\n')
                .expect("fixture bundle entry must have a header line");
            let metadata = header
                .strip_prefix("@@WPT-FIXTURE ")
                .expect("fixture bundle entry must start with @@WPT-FIXTURE");
            let name = metadata
                .split_ascii_whitespace()
                .next()
                .expect("fixture bundle entry must include an upstream name");

            fixtures.push(Fixture {
                name: name.to_owned(),
                source: source.to_owned(),
            });
        }
    }

    fixtures
}

fn is_section_marker(line: &str) -> bool {
    matches!(
        line,
        "#new-errors" | "#document-fragment" | "#script-off" | "#script-on" | "#document"
    )
}

fn parse_fixture_cases(source: &str) -> Result<Vec<TestCase>, String> {
    let lines: Vec<&str> = source.split('\n').collect();
    let mut cursor = 0usize;
    let mut cases = Vec::new();

    while cursor < lines.len() {
        while cursor < lines.len() && lines[cursor].is_empty() {
            cursor += 1;
        }
        if cursor >= lines.len() {
            break;
        }

        let mut description = String::new();
        if lines[cursor] == "#description" {
            cursor += 1;
            let description_start = cursor;
            while cursor < lines.len() && lines[cursor] != "#data" {
                cursor += 1;
            }
            if cursor >= lines.len() {
                return Err("description section is not followed by #data".to_owned());
            }
            description = lines[description_start..cursor].join(" ").trim().to_owned();
        }

        if lines.get(cursor) != Some(&"#data") {
            return Err(format!(
                "expected #data at line {}, found {:?}",
                cursor + 1,
                lines.get(cursor)
            ));
        }
        cursor += 1;

        let data_start = cursor;
        while cursor < lines.len() && lines[cursor] != "#errors" {
            cursor += 1;
        }
        if cursor >= lines.len() {
            return Err("test case has no #errors section".to_owned());
        }
        let input = lines[data_start..cursor].join("\n");
        cursor += 1;

        let mut has_parse_errors = false;
        while cursor < lines.len() && !is_section_marker(lines[cursor]) {
            has_parse_errors |= !lines[cursor].is_empty();
            cursor += 1;
        }

        let mut has_new_errors = false;
        let mut fragment_context = None;
        let mut scripting_mode = None;

        loop {
            match lines.get(cursor).copied() {
                Some("#new-errors") => {
                    has_new_errors = true;
                    cursor += 1;
                    while cursor < lines.len() && !is_section_marker(lines[cursor]) {
                        cursor += 1;
                    }
                }
                Some("#document-fragment") => {
                    cursor += 1;
                    let context = lines
                        .get(cursor)
                        .ok_or_else(|| "fragment marker has no context element".to_owned())?;
                    fragment_context = Some((*context).to_owned());
                    cursor += 1;
                }
                Some("#script-off") => {
                    scripting_mode = Some(false);
                    cursor += 1;
                }
                Some("#script-on") => {
                    scripting_mode = Some(true);
                    cursor += 1;
                }
                Some("#document") => {
                    cursor += 1;
                    break;
                }
                Some("") => {
                    cursor += 1;
                }
                Some(other) => {
                    return Err(format!("unexpected section marker {other:?}"));
                }
                None => return Err("test case has no #document tree".to_owned()),
            }
        }

        let mut expected_tree = Vec::new();
        let mut fragment_output = false;
        while cursor < lines.len() {
            if lines[cursor] == "#document-fragment" {
                fragment_output = true;
                cursor += 1;
                while cursor < lines.len() && !lines[cursor].is_empty() {
                    cursor += 1;
                }
                break;
            }

            if lines[cursor].is_empty() {
                let mut next = cursor;
                while next < lines.len() && lines[next].is_empty() {
                    next += 1;
                }
                if next >= lines.len() || lines[next] == "#data" || lines[next] == "#description" {
                    cursor = next;
                    break;
                }
                expected_tree.push(String::new());
                cursor += 1;
                continue;
            }

            expected_tree.push(lines[cursor].to_owned());
            cursor += 1;
        }

        if expected_tree.is_empty() {
            return Err("test case has an empty #document tree".to_owned());
        }

        cases.push(TestCase {
            description,
            input,
            has_parse_errors,
            has_new_errors,
            fragment_context,
            fragment_output,
            scripting_mode,
            expected_tree,
        });
    }

    Ok(cases)
}

fn parse_dump_line(line: &str) -> Result<(usize, &str), DumpError> {
    let Some(rest) = line.strip_prefix("| ") else {
        return Err(DumpError::Invalid(format!(
            "tree line does not start with '| ': {line:?}"
        )));
    };

    let spaces = rest
        .chars()
        .take_while(|character| *character == ' ')
        .count();
    if spaces % 2 != 0 {
        return Err(DumpError::Invalid(format!(
            "tree indentation is not a multiple of two: {line:?}"
        )));
    }

    Ok((spaces / 2, &rest[spaces..]))
}

fn parse_attribute_dump(content: &str) -> Option<(String, String)> {
    let (name, value) = content.split_once("=\"")?;
    if name.is_empty() || !value.ends_with('"') {
        return None;
    }
    Some((name.to_owned(), value[..value.len() - 1].to_owned()))
}

fn parse_dump_nodes(
    lines: &[String],
    cursor: &mut usize,
    depth: usize,
) -> Result<Vec<DumpNode>, DumpError> {
    let mut nodes = Vec::new();

    while *cursor < lines.len() {
        if lines[*cursor].is_empty() {
            return Err(DumpError::Unsupported(
                "multiline text in reference tree dump",
            ));
        }

        let (line_depth, content) = parse_dump_line(&lines[*cursor])?;
        if line_depth < depth {
            break;
        }
        if line_depth > depth {
            return Err(DumpError::Invalid(format!(
                "unexpected tree depth {} at line {}",
                line_depth,
                *cursor + 1
            )));
        }
        if parse_attribute_dump(content).is_some() {
            return Err(DumpError::Invalid(format!(
                "orphan attribute line at depth {depth}: {content:?}"
            )));
        }

        if let Some(doctype) = content
            .strip_prefix("<!DOCTYPE ")
            .and_then(|s| s.strip_suffix('>'))
        {
            let names: Vec<&str> = doctype.split_ascii_whitespace().collect();
            if names.len() != 1 {
                return Err(DumpError::Unsupported(
                    "public/system doctype identifiers are not represented by HARI NodeKind",
                ));
            }
            nodes.push(DumpNode::Doctype(names[0].to_ascii_lowercase()));
            *cursor += 1;
            continue;
        }

        if content.starts_with("<?") {
            return Err(DumpError::Unsupported(
                "processing-instruction nodes are not represented by HARI NodeKind",
            ));
        }

        if let Some(comment) = content
            .strip_prefix("<!-- ")
            .and_then(|s| s.strip_suffix(" -->"))
        {
            nodes.push(DumpNode::Comment(comment.to_owned()));
            *cursor += 1;
            continue;
        }

        if content == "content" {
            return Err(DumpError::Unsupported(
                "template content fragments are not represented by the HARI tree model",
            ));
        }

        if let Some(first_line) = content.strip_prefix('"') {
            let text = if let Some(single_line) = first_line.strip_suffix('"') {
                *cursor += 1;
                single_line.to_owned()
            } else {
                let mut text = first_line.to_owned();
                *cursor += 1;
                let mut terminated = false;

                while *cursor < lines.len() {
                    let continuation = &lines[*cursor];
                    text.push('\n');

                    if let Some(final_text) = continuation.strip_suffix('"') {
                        text.push_str(final_text);
                        *cursor += 1;
                        terminated = true;
                        break;
                    }

                    text.push_str(continuation);
                    *cursor += 1;
                }

                if !terminated {
                    return Err(DumpError::Invalid(
                        "unterminated multiline text node in reference tree dump".to_owned(),
                    ));
                }

                text
            };

            nodes.push(DumpNode::Text(text));
            continue;
        }

        if let Some(tag_name) = content.strip_prefix('<').and_then(|s| s.strip_suffix('>')) {
            if tag_name.starts_with("svg ") || tag_name.starts_with("math ") {
                return Err(DumpError::Unsupported(
                    "foreign-content namespaces are not represented by the HARI tree model",
                ));
            }

            *cursor += 1;
            let mut attributes = Vec::new();

            while *cursor < lines.len() {
                let (attribute_depth, attribute_content) = parse_dump_line(&lines[*cursor])?;
                if attribute_depth != depth + 1 {
                    break;
                }
                let Some((name, value)) = parse_attribute_dump(attribute_content) else {
                    break;
                };
                if name == "xmlns"
                    || name.starts_with("xmlns ")
                    || name.starts_with("xlink ")
                    || name.starts_with("xml ")
                {
                    return Err(DumpError::Unsupported(
                        "namespaced attributes are not represented by the HARI tree model",
                    ));
                }
                attributes.push((name, value));
                *cursor += 1;
            }

            let children = parse_dump_nodes(lines, cursor, depth + 1)?;
            nodes.push(DumpNode::Element {
                tag_name: tag_name.to_owned(),
                attributes,
                children,
            });
            continue;
        }

        return Err(DumpError::Invalid(format!(
            "unrecognized reference tree node: {content:?}"
        )));
    }

    Ok(nodes)
}

fn parse_reference_tree(lines: &[String]) -> Result<Vec<DumpNode>, DumpError> {
    let mut cursor = 0usize;
    let nodes = parse_dump_nodes(lines, &mut cursor, 0)?;
    if cursor != lines.len() {
        return Err(DumpError::Invalid(format!(
            "reference tree parser stopped at line {} of {}",
            cursor + 1,
            lines.len()
        )));
    }
    Ok(nodes)
}

fn serialize_hari_node(node: &Node) -> Result<DumpNode, &'static str> {
    match &node.kind {
        NodeKind::Document => Err("nested document node"),
        NodeKind::Doctype(value) => {
            let mut parts = value.split_ascii_whitespace();
            let prefix = parts.next().unwrap_or_default();
            if !prefix.eq_ignore_ascii_case("doctype") {
                return Err("unrecognized doctype representation");
            }
            let name = parts.next().ok_or("doctype has no name")?;
            if parts.next().is_some() {
                return Err(
                    "public/system doctype identifiers are not represented by HARI NodeKind",
                );
            }
            Ok(DumpNode::Doctype(name.to_ascii_lowercase()))
        }
        NodeKind::Comment(value) => {
            if value.contains('\n') {
                return Err("multiline comments are not supported by the v1 dump comparator");
            }
            Ok(DumpNode::Comment(value.clone()))
        }
        NodeKind::Text(value) => Ok(DumpNode::Text(value.clone())),
        NodeKind::Element { tag_name } => {
            let mut attributes: Vec<&Attribute> = node.attributes.iter().collect();
            attributes
                .sort_by(|left, right| left.name.encode_utf16().cmp(right.name.encode_utf16()));

            let mut serialized_attributes = Vec::with_capacity(attributes.len());
            for attribute in attributes {
                if attribute.name.contains(':') || attribute.name == "xmlns" {
                    return Err("namespaced attributes are not represented by the HARI tree model");
                }
                if attribute.value.contains('\n') {
                    return Err(
                        "multiline attribute values are unsupported by the v1 dump comparator",
                    );
                }
                serialized_attributes.push((attribute.name.clone(), attribute.value.clone()));
            }

            let children = node
                .children
                .iter()
                .map(serialize_hari_node)
                .collect::<Result<Vec<_>, _>>()?;

            Ok(DumpNode::Element {
                tag_name: tag_name.clone(),
                attributes: serialized_attributes,
                children,
            })
        }
    }
}

fn serialize_hari_document(document: &Node) -> Result<Vec<DumpNode>, &'static str> {
    if !matches!(&document.kind, NodeKind::Document) {
        return Err("parser root is not a document node");
    }

    document.children.iter().map(serialize_hari_node).collect()
}

fn wrapper_flags(node: &Node, flags: &mut [bool; 3]) {
    if let NodeKind::Element { tag_name } = &node.kind {
        match tag_name.as_str() {
            "html" => flags[0] = true,
            "head" => flags[1] = true,
            "body" => flags[2] = true,
            _ => {}
        }
    }

    for child in &node.children {
        wrapper_flags(child, flags);
    }
}

fn project_implicit_body(expected: &[DumpNode]) -> Result<Vec<DumpNode>, &'static str> {
    let html_positions: Vec<usize> = expected
        .iter()
        .enumerate()
        .filter_map(|(index, node)| {
            matches!(node, DumpNode::Element { tag_name, .. } if tag_name == "html")
                .then_some(index)
        })
        .collect();

    if html_positions.len() != 1 {
        return Err("body projection requires exactly one implicit html element");
    }

    let html_index = html_positions[0];
    let DumpNode::Element {
        attributes: html_attributes,
        children: html_children,
        ..
    } = &expected[html_index]
    else {
        unreachable!("html position was selected from an element")
    };

    if !html_attributes.is_empty() {
        return Err("implicit html wrapper unexpectedly has attributes");
    }

    let mut head_count = 0usize;
    let mut body_count = 0usize;
    let mut body_children = Vec::new();

    for child in html_children {
        match child {
            DumpNode::Element {
                tag_name,
                attributes,
                children,
            } if tag_name == "head" => {
                head_count += 1;
                if !attributes.is_empty() || !children.is_empty() {
                    return Err("implicit head content is outside the v1 body projection");
                }
            }
            DumpNode::Element {
                tag_name,
                attributes,
                children,
            } if tag_name == "body" => {
                body_count += 1;
                if !attributes.is_empty() {
                    return Err("implicit body wrapper unexpectedly has attributes");
                }
                body_children = children.clone();
            }
            _ => {
                return Err("implicit html wrapper contains an unprojectable child");
            }
        }
    }

    if head_count != 1 || body_count != 1 {
        return Err("body projection requires one empty head and one body element");
    }

    let mut projected = expected[..html_index].to_vec();
    projected.extend(body_children);
    projected.extend(expected[html_index + 1..].iter().cloned());
    Ok(projected)
}

fn contains_ascii_case_insensitive(source: &str, needle: &str) -> bool {
    source
        .as_bytes()
        .windows(needle.len())
        .any(|window| window.eq_ignore_ascii_case(needle.as_bytes()))
}

fn expects_error_metadata(case: &TestCase) -> bool {
    case.has_parse_errors || case.has_new_errors
}

fn known_unsupported_case(case: &TestCase) -> Option<&'static str> {
    if case.fragment_context.is_some() || case.fragment_output {
        return Some("HTML fragment parsing is not implemented");
    }
    if case.scripting_mode.is_some() {
        return Some("WPT scripting-mode variants are not implemented");
    }
    if contains_ascii_case_insensitive(&case.input, "<template") {
        return Some("template contents are not represented by the HARI tree model");
    }
    if contains_ascii_case_insensitive(&case.input, "<svg")
        || contains_ascii_case_insensitive(&case.input, "<math")
    {
        return Some("foreign-content namespace parsing is not implemented");
    }
    if contains_ascii_case_insensitive(&case.input, "<noscript") {
        return Some("noscript parsing depends on an unimplemented scripting flag");
    }
    let lower_input = case.input.to_ascii_lowercase();
    if let Some(close_tag) = lower_input.rfind("</html>") {
        let trailing = &lower_input[close_tag + "</html>".len()..];
        if !trailing.is_empty() && trailing.bytes().all(|byte| byte.is_ascii_whitespace()) {
            return Some(
                "trailing document whitespace after an explicit html end tag is not preserved by the current parser",
            );
        }
    }
    if contains_ascii_case_insensitive(&case.input, "<isindex") {
        return Some("legacy isindex insertion behavior is not implemented");
    }
    if contains_ascii_case_insensitive(&case.input, "<frameset")
        || contains_ascii_case_insensitive(&case.input, "<frame")
    {
        return Some("frameset insertion mode is not implemented");
    }
    if contains_ascii_case_insensitive(&case.input, "<plaintext") {
        return Some("plaintext tokenizer state is not implemented");
    }
    if contains_ascii_case_insensitive(&case.input, "<select") {
        return Some("select insertion mode is not implemented");
    }
    None
}

fn mismatch_summary(expected: &[DumpNode], actual: &[DumpNode]) -> String {
    let common = expected.len().min(actual.len());
    for index in 0..common {
        if expected[index] != actual[index] {
            return format!(
                "first differing document child {index}: expected {:?}, actual {:?}",
                expected[index], actual[index]
            );
        }
    }
    format!(
        "document child count differs: expected {}, actual {}",
        expected.len(),
        actual.len()
    )
}

fn evaluate_case(fixture_name: &str, case: &TestCase) -> CaseResult {
    if matches!(fixture_name, "adoption01" | "adoption02") {
        return CaseResult::Unsupported(
            "adoption-agency formatting reconstruction is not implemented",
        );
    }
    if let Some(reason) = known_unsupported_case(case) {
        return CaseResult::Unsupported(reason);
    }

    let document = match parse_document(case.input.as_bytes(), &HtmlLimits::default()) {
        Ok(document) => document,
        Err(error) => {
            return CaseResult::Invalid(format!("HARI parser rejected input: {error:?}"));
        }
    };

    let expected = match parse_reference_tree(&case.expected_tree) {
        Ok(expected) => expected,
        Err(DumpError::Unsupported(reason)) => return CaseResult::Unsupported(reason),
        Err(DumpError::Invalid(reason)) => {
            return CaseResult::Invalid(format!("invalid WPT reference dump: {reason}"));
        }
    };

    let actual = match serialize_hari_document(&document) {
        Ok(actual) => actual,
        Err(reason) => return CaseResult::Unsupported(reason),
    };

    let mut wrappers = [false; 3];
    wrapper_flags(&document, &mut wrappers);

    let (mode, expected_for_comparison) = if wrappers.iter().all(|present| *present) {
        (ComparisonMode::ExactTree, expected)
    } else if wrappers.iter().any(|present| *present) {
        return CaseResult::Unsupported(
            "partial authored html/head/body wrappers require unimplemented wrapper insertion",
        );
    } else {
        match project_implicit_body(&expected) {
            Ok(projected) => (ComparisonMode::BodyProjection, projected),
            Err(reason) => return CaseResult::Unsupported(reason),
        }
    };

    if expected_for_comparison == actual {
        CaseResult::Passed(mode)
    } else {
        CaseResult::Failed(mismatch_summary(&expected_for_comparison, &actual))
    }
}

#[test]
fn pinned_wpt_tree_construction_fixtures_are_compared_deterministically() {
    let fixtures = load_fixtures();
    let unique_names: HashSet<&str> = fixtures
        .iter()
        .map(|fixture| fixture.name.as_str())
        .collect();
    assert_eq!(fixtures.len(), 50, "the v1 corpus must contain 50 fixtures");
    assert_eq!(
        unique_names.len(),
        fixtures.len(),
        "fixture names must be unique"
    );

    let mut total_cases = 0usize;
    let mut exact_tree_passes = 0usize;
    let mut body_projection_passes = 0usize;
    let mut expected_error_metadata_cases = 0usize;
    let mut expected_new_error_cases = 0usize;
    let mut tree_compared_error_cases = 0usize;
    let mut tree_passed_error_cases = 0usize;
    let mut unsupported = BTreeMap::<&'static str, usize>::new();
    let mut failures = Vec::new();
    let mut expected_error_tree_mismatches = Vec::new();
    let mut expected_error_precomparison_failures = Vec::new();

    for fixture in &fixtures {
        let cases = parse_fixture_cases(&fixture.source)
            .unwrap_or_else(|error| panic!("failed to read WPT fixture {}: {error}", fixture.name));

        for (case_index, case) in cases.iter().enumerate() {
            total_cases += 1;
            let expects_errors = expects_error_metadata(case);
            if expects_errors {
                expected_error_metadata_cases += 1;
            }
            if case.has_new_errors {
                expected_new_error_cases += 1;
            }

            let result = evaluate_case(&fixture.name, case);
            if expects_errors && matches!(&result, CaseResult::Passed(_) | CaseResult::Failed(_)) {
                tree_compared_error_cases += 1;
            }
            if expects_errors && matches!(&result, CaseResult::Passed(_)) {
                tree_passed_error_cases += 1;
            }

            match result {
                CaseResult::Passed(ComparisonMode::ExactTree) => exact_tree_passes += 1,
                CaseResult::Passed(ComparisonMode::BodyProjection) => body_projection_passes += 1,
                CaseResult::Unsupported(reason) => {
                    *unsupported.entry(reason).or_default() += 1;
                }
                CaseResult::Failed(reason) => {
                    let label = if case.description.is_empty() {
                        format!("case {}", case_index + 1)
                    } else {
                        case.description.clone()
                    };
                    let failure = format!("{} / {label}: {reason}", fixture.name);
                    if expects_errors {
                        expected_error_tree_mismatches.push(failure);
                    } else {
                        failures.push(failure);
                    }
                }
                CaseResult::Invalid(reason) => {
                    let label = if case.description.is_empty() {
                        format!("case {}", case_index + 1)
                    } else {
                        case.description.clone()
                    };
                    let failure = format!("{} / {label}: {reason}", fixture.name);
                    if expects_errors {
                        expected_error_precomparison_failures.push(failure);
                    } else {
                        failures.push(failure);
                    }
                }
            }
        }
    }

    let passed = exact_tree_passes + body_projection_passes;
    let unsupported_total: usize = unsupported.values().sum();
    let expected_error_evaluation_failures =
        expected_error_tree_mismatches.len() + expected_error_precomparison_failures.len();
    eprintln!(
        "HARI HTML Conformance v1.2 (WPT {WPT_REVISION}): fixtures={}, cases={}, exact-tree-pass={}, body-projection-pass={}, non-error-failures={}, expected-error-evaluation-failures={}, expected-error-tree-mismatches={}, expected-error-precomparison-failures={}, unsupported={}, expected-error-metadata={}, expected-new-error-metadata={}, tree-compared-error-cases={}, tree-passed-error-cases={}, diagnostics-unavailable={}",
        fixtures.len(),
        total_cases,
        exact_tree_passes,
        body_projection_passes,
        failures.len(),
        expected_error_evaluation_failures,
        expected_error_tree_mismatches.len(),
        expected_error_precomparison_failures.len(),
        unsupported_total,
        expected_error_metadata_cases,
        expected_new_error_cases,
        tree_compared_error_cases,
        tree_passed_error_cases,
        expected_error_metadata_cases
    );
    for (reason, count) in &unsupported {
        eprintln!("  unsupported / {reason}: {count}");
    }
    for failure in failures.iter().take(20) {
        eprintln!("  FAIL / {failure}");
    }
    for failure in expected_error_tree_mismatches.iter().take(20) {
        eprintln!("  EXPECTED-ERROR TREE MISMATCH / {failure}");
    }
    for failure in expected_error_precomparison_failures.iter().take(20) {
        eprintln!("  EXPECTED-ERROR PRE-COMPARISON FAILURE / {failure}");
    }

    assert!(
        expected_error_metadata_cases >= 50,
        "the pinned WPT corpus must contain at least 50 cases with expected parse-error metadata; found {expected_error_metadata_cases}"
    );
    assert!(
        tree_compared_error_cases >= 50,
        "at least 50 expected-error cases must reach tree comparison; found {tree_compared_error_cases}"
    );
    assert!(
        passed >= 66,
        "WPT v1.2 must preserve the existing 66-pass v1 baseline; found {passed}"
    );
    assert!(
        expected_error_evaluation_failures <= MAX_EXPECTED_ERROR_EVALUATION_FAILURES,
        "expected-error evaluation gaps increased beyond the pinned v1.2 baseline of {MAX_EXPECTED_ERROR_EVALUATION_FAILURES}; found {expected_error_evaluation_failures}"
    );
    assert!(
        failures.is_empty(),
        "{} supported WPT tree-construction cases failed; see the deterministic diagnostics above",
        failures.len()
    );
}

#[cfg(test)]
mod tests {
    use super::{
        CaseResult, ComparisonMode, DumpNode, TestCase, evaluate_case, parse_fixture_cases,
        parse_reference_tree, project_implicit_body, serialize_hari_node,
    };
    use hari_engine_html::{Attribute, Node, NodeKind};

    #[test]
    fn fixture_reader_preserves_input_and_reads_multiple_cases() {
        let fixture = "#description\nsimple case\n#data\n<p>One\n#errors\n#document\n| <html>\n|   <head>\n|   <body>\n|     <p>\n|       \"One\"\n\n#data\n<p>Two\n#errors\n#document\n| <html>\n|   <head>\n|   <body>\n|     <p>\n|       \"Two\"\n";
        let cases = parse_fixture_cases(fixture).unwrap();
        assert_eq!(cases.len(), 2);
        assert_eq!(cases[0].description, "simple case");
        assert_eq!(cases[0].input, "<p>One");
        assert_eq!(cases[1].input, "<p>Two");
    }

    #[test]
    fn body_projection_preserves_doctype_and_body_children() {
        let expected = vec![
            DumpNode::Doctype("html".to_owned()),
            DumpNode::Element {
                tag_name: "html".to_owned(),
                attributes: Vec::new(),
                children: vec![
                    DumpNode::Element {
                        tag_name: "head".to_owned(),
                        attributes: Vec::new(),
                        children: Vec::new(),
                    },
                    DumpNode::Element {
                        tag_name: "body".to_owned(),
                        attributes: Vec::new(),
                        children: vec![DumpNode::Element {
                            tag_name: "p".to_owned(),
                            attributes: Vec::new(),
                            children: vec![DumpNode::Text("HARI".to_owned())],
                        }],
                    },
                ],
            },
        ];

        assert_eq!(
            project_implicit_body(&expected).unwrap(),
            vec![
                DumpNode::Doctype("html".to_owned()),
                DumpNode::Element {
                    tag_name: "p".to_owned(),
                    attributes: Vec::new(),
                    children: vec![DumpNode::Text("HARI".to_owned())],
                },
            ]
        );
    }

    #[test]
    fn exact_reference_dump_parses_attributes_and_text() {
        let lines = vec![
            "| <html>".to_owned(),
            "|   <head>".to_owned(),
            "|   <body>".to_owned(),
            "|     <p>".to_owned(),
            "|       id=\"main\"".to_owned(),
            "|       \"HARI\"".to_owned(),
        ];
        let tree = parse_reference_tree(&lines).unwrap();
        assert!(matches!(
            tree.as_slice(),
            [DumpNode::Element { tag_name, children, .. }] if tag_name == "html" && children.len() == 2
        ));
    }

    #[test]
    fn authored_document_wrappers_use_exact_tree_comparison() {
        let case = TestCase {
            description: "all document wrappers are authored".to_owned(),
            input: "<!DOCTYPE html><html><head></head><body><p id=\"main\">HARI</p></body></html>"
                .to_owned(),
            has_parse_errors: false,
            has_new_errors: false,
            fragment_context: None,
            fragment_output: false,
            scripting_mode: None,
            expected_tree: vec![
                "| <!DOCTYPE html>".to_owned(),
                "| <html>".to_owned(),
                "|   <head>".to_owned(),
                "|   <body>".to_owned(),
                "|     <p>".to_owned(),
                "|       id=\"main\"".to_owned(),
                "|       \"HARI\"".to_owned(),
            ],
        };

        assert!(matches!(
            evaluate_case("tests1", &case),
            CaseResult::Passed(ComparisonMode::ExactTree)
        ));
    }

    fn simple_error_case(has_parse_errors: bool, has_new_errors: bool) -> TestCase {
        TestCase {
            description: "simple body tree with expected parse-error metadata".to_owned(),
            input: "<p>HARI".to_owned(),
            has_parse_errors,
            has_new_errors,
            fragment_context: None,
            fragment_output: false,
            scripting_mode: None,
            expected_tree: vec![
                "| <html>".to_owned(),
                "|   <head>".to_owned(),
                "|   <body>".to_owned(),
                "|     <p>".to_owned(),
                "|       \"HARI\"".to_owned(),
            ],
        }
    }

    #[test]
    fn parse_error_expectations_do_not_block_tree_comparison() {
        let case = simple_error_case(true, false);

        assert!(matches!(
            evaluate_case("inbody01", &case),
            CaseResult::Passed(ComparisonMode::BodyProjection)
        ));
    }

    #[test]
    fn new_error_expectations_do_not_block_tree_comparison() {
        let case = simple_error_case(false, true);

        assert!(matches!(
            evaluate_case("inbody01", &case),
            CaseResult::Passed(ComparisonMode::BodyProjection)
        ));
    }

    #[test]
    fn fixture_reader_preserves_error_metadata_separately() {
        let fixture = "#data\n<p>HARI\n#errors\n(1,1): expected-doctype-but-got-start-tag\n#new-errors\n(1,1): unexpected-token\n#document\n| <html>\n|   <head>\n|   <body>\n|     <p>\n|       \"HARI\"\n";
        let cases = parse_fixture_cases(fixture).unwrap();

        assert_eq!(cases.len(), 1);
        assert!(cases[0].has_parse_errors);
        assert!(cases[0].has_new_errors);
    }

    #[test]
    fn reference_dump_preserves_multiline_text_nodes() {
        let lines = vec![
            "| <html>".to_owned(),
            "|   <head>".to_owned(),
            "|   <body>".to_owned(),
            "|     <pre>".to_owned(),
            "|       \"first".to_owned(),
            "| literal tree prefix".to_owned(),
            String::new(),
            "third\"".to_owned(),
        ];
        let expected = vec![DumpNode::Element {
            tag_name: "html".to_owned(),
            attributes: Vec::new(),
            children: vec![
                DumpNode::Element {
                    tag_name: "head".to_owned(),
                    attributes: Vec::new(),
                    children: Vec::new(),
                },
                DumpNode::Element {
                    tag_name: "body".to_owned(),
                    attributes: Vec::new(),
                    children: vec![DumpNode::Element {
                        tag_name: "pre".to_owned(),
                        attributes: Vec::new(),
                        children: vec![DumpNode::Text(
                            "first\n| literal tree prefix\n\nthird".to_owned(),
                        )],
                    }],
                },
            ],
        }];

        let parsed = parse_reference_tree(&lines).unwrap();
        assert_eq!(parsed, expected);

        let text = Node {
            kind: NodeKind::Text("first\n| literal tree prefix\n\nthird".to_owned()),
            attributes: Vec::new(),
            children: Vec::new(),
        };
        assert_eq!(
            serialize_hari_node(&text).unwrap(),
            DumpNode::Text("first\n| literal tree prefix\n\nthird".to_owned())
        );
    }

    #[test]
    fn unterminated_multiline_text_dump_is_invalid() {
        let lines = vec![
            "| <html>".to_owned(),
            "|   <head>".to_owned(),
            "|   <body>".to_owned(),
            "|     \"unfinished".to_owned(),
            "text".to_owned(),
        ];

        assert!(matches!(
            parse_reference_tree(&lines),
            Err(super::DumpError::Invalid(_))
        ));
    }

    #[test]
    fn serializer_orders_attributes_by_utf16_code_units() {
        let node = Node {
            kind: NodeKind::Element {
                tag_name: "p".to_owned(),
            },
            attributes: vec![
                Attribute {
                    name: "\u{E000}".to_owned(),
                    value: "bmp".to_owned(),
                },
                Attribute {
                    name: "\u{10000}".to_owned(),
                    value: "supplementary".to_owned(),
                },
            ],
            children: Vec::new(),
        };

        let serialized = serialize_hari_node(&node).unwrap();
        let DumpNode::Element { attributes, .. } = serialized else {
            panic!("expected element");
        };
        assert_eq!(attributes[0].0, "\u{10000}");
        assert_eq!(attributes[1].0, "\u{E000}");
    }
}

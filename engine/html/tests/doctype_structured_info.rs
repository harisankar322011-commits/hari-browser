use hari_engine_html::{DoctypeInfo, HtmlLimits, Node, NodeKind, parse_document};

fn parse_doctype(input: &[u8]) -> DoctypeInfo {
    let document = parse_document(input, &HtmlLimits::default()).unwrap();
    document
        .children
        .first()
        .and_then(Node::doctype_info)
        .expect("the first child should expose structured DOCTYPE information")
}

#[test]
fn ordinary_html_doctype_has_only_a_name() {
    let info = parse_doctype(b"<!DOCTYPE html>");

    assert_eq!(
        info,
        DoctypeInfo {
            name: "html".to_owned(),
            public_identifier: None,
            system_identifier: None,
            force_quirks: false,
        }
    );
}

#[test]
fn public_and_system_identifiers_are_exposed_without_losing_embedded_greater_than() {
    let info = parse_doctype(
        br#"<!DOCTYPE html PUBLIC "-//HARI>PUBLIC//EN" "https://example.test/a>b.dtd">"#,
    );

    assert_eq!(info.name, "html");
    assert_eq!(
        info.public_identifier.as_deref(),
        Some("-//HARI>PUBLIC//EN")
    );
    assert_eq!(
        info.system_identifier.as_deref(),
        Some("https://example.test/a>b.dtd")
    );
    assert!(!info.force_quirks);
}

#[test]
fn public_identifier_can_appear_without_a_system_identifier() {
    let info = parse_doctype(b"<!DOCTYPE html PUBLIC 'HARI PUBLIC ID'>");

    assert_eq!(info.public_identifier.as_deref(), Some("HARI PUBLIC ID"));
    assert_eq!(info.system_identifier, None);
    assert!(!info.force_quirks);
}

#[test]
fn system_identifier_requires_whitespace_after_the_public_identifier() {
    let info = parse_doctype(b"<!DOCTYPE html PUBLIC 'HARI PUBLIC''HARI SYSTEM'>");

    assert_eq!(info.public_identifier.as_deref(), Some("HARI PUBLIC"));
    assert_eq!(info.system_identifier, None);
    assert!(info.force_quirks);
}

#[test]
fn unseparated_second_quote_does_not_extend_the_doctype_token() {
    let document = parse_document(
        b"<!DOCTYPE html PUBLIC 'HARI PUBLIC''SYSTEM>tail",
        &HtmlLimits::default(),
    )
    .unwrap();

    assert_eq!(document.children.len(), 2);
    let info = document.children[0].doctype_info().unwrap();
    assert_eq!(info.public_identifier.as_deref(), Some("HARI PUBLIC"));
    assert_eq!(info.system_identifier, None);
    assert!(info.force_quirks);
    assert!(matches!(
        &document.children[1].kind,
        NodeKind::Text(value) if value == "tail"
    ));
}

#[test]
fn system_identifier_is_exposed() {
    let info = parse_doctype(b"<!DOCTYPE html SYSTEM 'https://example.test/hari.dtd'>");

    assert_eq!(info.name, "html");
    assert_eq!(
        info.system_identifier.as_deref(),
        Some("https://example.test/hari.dtd")
    );
    assert_eq!(info.public_identifier, None);
    assert!(!info.force_quirks);
}

#[test]
fn doctype_name_and_keywords_are_ascii_case_insensitive() {
    let info = parse_doctype(b"<!dOcTyPe HTML pUbLiC 'HARI PUBLIC' 'HARI SYSTEM'>");

    assert_eq!(info.name, "html");
    assert_eq!(info.public_identifier.as_deref(), Some("HARI PUBLIC"));
    assert_eq!(info.system_identifier.as_deref(), Some("HARI SYSTEM"));
    assert!(!info.force_quirks);
}

#[test]
fn missing_doctype_name_sets_force_quirks() {
    let info = parse_doctype(b"<!DOCTYPE>");

    assert!(info.name.is_empty());
    assert!(info.force_quirks);
    assert_eq!(info.public_identifier, None);
    assert_eq!(info.system_identifier, None);
}

#[test]
fn unknown_tokens_after_the_name_set_force_quirks() {
    let info = parse_doctype(b"<!DOCTYPE html HARI 'ignored'>");

    assert_eq!(info.name, "html");
    assert!(info.force_quirks);
    assert_eq!(info.public_identifier, None);
    assert_eq!(info.system_identifier, None);
}

#[test]
fn missing_public_identifier_sets_force_quirks() {
    let info = parse_doctype(b"<!DOCTYPE html PUBLIC>");

    assert_eq!(info.name, "html");
    assert!(info.force_quirks);
    assert_eq!(info.public_identifier, None);
}

#[test]
fn unterminated_system_identifier_is_preserved_and_sets_force_quirks() {
    let info = parse_doctype(b"<!DOCTYPE html SYSTEM \"unfinished > identifier");

    assert_eq!(info.name, "html");
    assert_eq!(
        info.system_identifier.as_deref(),
        Some("unfinished > identifier")
    );
    assert!(info.force_quirks);
}

#[test]
fn unexpected_trailing_tokens_after_an_identifier_set_force_quirks() {
    let info = parse_doctype(b"<!DOCTYPE html SYSTEM 'hari.dtd' trailing");

    assert_eq!(info.system_identifier.as_deref(), Some("hari.dtd"));
    assert!(info.force_quirks);
}

#[test]
fn non_doctype_nodes_do_not_expose_doctype_information() {
    let document = parse_document(b"<p>HARI</p>", &HtmlLimits::default()).unwrap();

    assert!(document.doctype_info().is_none());
    assert!(matches!(
        &document.children[0].kind,
        NodeKind::Element { .. }
    ));
}

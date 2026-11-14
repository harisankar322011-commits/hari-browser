use hari_engine_html::{DoctypeInfo, DoctypeMode, HtmlLimits, Node, parse_document};

fn doctype_info(input: &str) -> DoctypeInfo {
    let document = parse_document(input.as_bytes(), &HtmlLimits::default()).unwrap();
    document
        .children
        .first()
        .and_then(Node::doctype_info)
        .expect("the first document child should be a DOCTYPE")
}

fn mode(input: &str) -> DoctypeMode {
    doctype_info(input).compatibility_mode()
}

#[test]
fn modern_and_legacy_compat_doctypes_select_no_quirks_mode() {
    assert_eq!(mode("<!DOCTYPE html>"), DoctypeMode::NoQuirks);
    assert_eq!(
        mode("<!DOCTYPE html SYSTEM 'about:legacy-compat'>"),
        DoctypeMode::NoQuirks
    );
    assert_eq!(
        mode("<!DOCTYPE html PUBLIC '-//W3C//DTD XHTML 1.0 Strict//EN'>"),
        DoctypeMode::NoQuirks
    );
}

#[test]
fn force_quirks_and_non_html_names_select_quirks_mode() {
    assert_eq!(mode("<!DOCTYPE html PUBLIC>"), DoctypeMode::Quirks);
    assert_eq!(mode("<!DOCTYPE svg>"), DoctypeMode::Quirks);
    assert_eq!(mode("<!DOCTYPE HTML HARI>"), DoctypeMode::Quirks);
}

#[test]
fn exact_legacy_public_and_system_identifiers_select_quirks_mode() {
    for public_identifier in [
        "-//W3O//DTD W3 HTML Strict 3.0//EN//",
        "-/W3C/DTD HTML 4.0 Transitional/EN",
        "HTML",
    ] {
        let input = format!("<!DOCTYPE html PUBLIC '{public_identifier}'>");
        assert_eq!(mode(&input), DoctypeMode::Quirks, "{public_identifier}");
    }

    assert_eq!(
        mode(
            "<!DOCTYPE html SYSTEM 'http://www.ibm.com/data/dtd/v11/ibmxhtml1-transitional.dtd'>"
        ),
        DoctypeMode::Quirks
    );
}

#[test]
fn every_legacy_public_identifier_prefix_selects_quirks_mode() {
    const PREFIXES: &[&str] = &[
        "+//Silmaril//dtd html Pro v0r11 19970101//",
        "-//AS//DTD HTML 3.0 asWedit + extensions//",
        "-//AdvaSoft Ltd//DTD HTML 3.0 asWedit + extensions//",
        "-//IETF//DTD HTML 2.0 Level 1//",
        "-//IETF//DTD HTML 2.0 Level 2//",
        "-//IETF//DTD HTML 2.0 Strict Level 1//",
        "-//IETF//DTD HTML 2.0 Strict Level 2//",
        "-//IETF//DTD HTML 2.0 Strict//",
        "-//IETF//DTD HTML 2.0//",
        "-//IETF//DTD HTML 2.1E//",
        "-//IETF//DTD HTML 3.0//",
        "-//IETF//DTD HTML 3.2 Final//",
        "-//IETF//DTD HTML 3.2//",
        "-//IETF//DTD HTML 3//",
        "-//IETF//DTD HTML Level 0//",
        "-//IETF//DTD HTML Level 1//",
        "-//IETF//DTD HTML Level 2//",
        "-//IETF//DTD HTML Level 3//",
        "-//IETF//DTD HTML Strict Level 0//",
        "-//IETF//DTD HTML Strict Level 1//",
        "-//IETF//DTD HTML Strict Level 2//",
        "-//IETF//DTD HTML Strict Level 3//",
        "-//IETF//DTD HTML Strict//",
        "-//IETF//DTD HTML//",
        "-//Metrius//DTD Metrius Presentational//",
        "-//Microsoft//DTD Internet Explorer 2.0 HTML Strict//",
        "-//Microsoft//DTD Internet Explorer 2.0 HTML//",
        "-//Microsoft//DTD Internet Explorer 2.0 Tables//",
        "-//Microsoft//DTD Internet Explorer 3.0 HTML Strict//",
        "-//Microsoft//DTD Internet Explorer 3.0 HTML//",
        "-//Microsoft//DTD Internet Explorer 3.0 Tables//",
        "-//Netscape Comm. Corp.//DTD HTML//",
        "-//Netscape Comm. Corp.//DTD Strict HTML//",
        "-//O'Reilly and Associates//DTD HTML 2.0//",
        "-//O'Reilly and Associates//DTD HTML Extended 1.0//",
        "-//O'Reilly and Associates//DTD HTML Extended Relaxed 1.0//",
        "-//SQ//DTD HTML 2.0 HoTMetaL + extensions//",
        "-//SoftQuad Software//DTD HoTMetaL PRO 6.0::19990601::extensions to HTML 4.0//",
        "-//SoftQuad//DTD HoTMetaL PRO 4.0::19971010::extensions to HTML 4.0//",
        "-//Spyglass//DTD HTML 2.0 Extended//",
        "-//Sun Microsystems Corp.//DTD HotJava HTML//",
        "-//Sun Microsystems Corp.//DTD HotJava Strict HTML//",
        "-//W3C//DTD HTML 3 1995-03-24//",
        "-//W3C//DTD HTML 3.2 Draft//",
        "-//W3C//DTD HTML 3.2 Final//",
        "-//W3C//DTD HTML 3.2//",
        "-//W3C//DTD HTML 3.2S Draft//",
        "-//W3C//DTD HTML Experimental 19960712//",
        "-//W3C//DTD HTML Experimental 970421//",
        "-//W3C//DTD W3 HTML//",
        "-//W3O//DTD W3 HTML 3.0//",
        "-//WebTechs//DTD Mozilla HTML 2.0//",
        "-//WebTechs//DTD Mozilla HTML//",
    ];

    for prefix in PREFIXES {
        let input = format!("<!DOCTYPE html PUBLIC '{prefix} suffix'>");
        assert_eq!(mode(&input), DoctypeMode::Quirks, "{prefix}");
    }
}

#[test]
fn xhtml_10_frameset_and_transitional_select_limited_quirks_mode() {
    for public_identifier in [
        "-//W3C//DTD XHTML 1.0 Frameset//EN",
        "-//W3C//DTD XHTML 1.0 Transitional//EN",
    ] {
        let input = format!("<!DOCTYPE html PUBLIC '{public_identifier}'>");
        assert_eq!(mode(&input), DoctypeMode::LimitedQuirks, "{public_identifier}");
    }
}

#[test]
fn html_401_frameset_and_transitional_depend_on_nonempty_system_identifier() {
    for public_identifier in [
        "-//W3C//DTD HTML 4.01 Frameset//EN",
        "-//W3C//DTD HTML 4.01 Transitional//EN",
    ] {
        let with_system =
            format!("<!DOCTYPE html PUBLIC '{public_identifier}' 'legacy.dtd'>");
        let without_system = format!("<!DOCTYPE html PUBLIC '{public_identifier}'>");
        let empty_system = format!("<!DOCTYPE html PUBLIC '{public_identifier}' ''>");

        assert_eq!(mode(&with_system), DoctypeMode::LimitedQuirks, "{public_identifier}");
        assert_eq!(mode(&without_system), DoctypeMode::Quirks, "{public_identifier}");
        assert_eq!(mode(&empty_system), DoctypeMode::Quirks, "{public_identifier}");
    }
}

#[test]
fn legacy_identifier_comparisons_are_ascii_case_insensitive() {
    assert_eq!(
        mode("<!DOCTYPE HTML PUBLIC '-//w3c//dtd xhtml 1.0 transitional//en'>"),
        DoctypeMode::LimitedQuirks
    );
    assert_eq!(
        mode("<!DOCTYPE html PUBLIC 'html'>"),
        DoctypeMode::Quirks
    );
    assert_eq!(
        mode("<!DOCTYPE html SYSTEM 'HTTP://WWW.IBM.COM/DATA/DTD/V11/IBMXHTML1-TRANSITIONAL.DTD'>"),
        DoctypeMode::Quirks
    );
}

#[test]
fn unlisted_public_and_system_identifiers_keep_no_quirks_mode() {
    assert_eq!(
        mode("<!DOCTYPE html PUBLIC '-//HARI//DTD MODERN HTML//EN' 'hari.dtd'>"),
        DoctypeMode::NoQuirks
    );
    assert_eq!(
        mode("<!DOCTYPE html SYSTEM 'https://example.test/hari.dtd'>"),
        DoctypeMode::NoQuirks
    );
}

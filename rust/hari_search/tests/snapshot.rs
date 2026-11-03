use std::io::Cursor;

use hari_search::{Document, SearchIndex, SnapshotError};

fn document(id: &str, url: &str, title: &str, body: &str) -> Document {
    Document {
        id: id.to_owned(),
        url: url.to_owned(),
        title: title.to_owned(),
        body: body.to_owned(),
    }
}

#[test]
fn snapshot_round_trip_rebuilds_the_search_index() {
    let mut original = SearchIndex::new();
    original
        .add_document(document(
            "malayalam",
            "https://example.org/kerala",
            "കേരള ചരിത്രം",
            "കേരളത്തിന്റെ സംസ്കാരവും ചരിത്രവും",
        ))
        .unwrap();
    original
        .add_document(document(
            "science",
            "https://example.org/space",
            "Space exploration",
            "Astronomy and physics",
        ))
        .unwrap();

    let mut bytes = Vec::new();
    original.write_snapshot(&mut bytes).unwrap();
    let restored = SearchIndex::read_snapshot(Cursor::new(bytes)).unwrap();

    assert_eq!(2, restored.document_count());
    assert_eq!(
        "malayalam",
        restored.search("കേരള ചരിത്രം", 5)[0].document_id
    );
    assert_eq!("science", restored.search("astronomy", 5)[0].document_id);
}

#[test]
fn snapshot_bytes_are_deterministic_for_the_same_documents() {
    let mut first = SearchIndex::new();
    first
        .add_document(document(
            "b",
            "https://example.org/b",
            "Beta",
            "shared",
        ))
        .unwrap();
    first
        .add_document(document(
            "a",
            "https://example.org/a",
            "Alpha",
            "shared",
        ))
        .unwrap();

    let mut second = SearchIndex::new();
    second
        .add_document(document(
            "a",
            "https://example.org/a",
            "Alpha",
            "shared",
        ))
        .unwrap();
    second
        .add_document(document(
            "b",
            "https://example.org/b",
            "Beta",
            "shared",
        ))
        .unwrap();

    let mut first_bytes = Vec::new();
    let mut second_bytes = Vec::new();
    first.write_snapshot(&mut first_bytes).unwrap();
    second.write_snapshot(&mut second_bytes).unwrap();

    assert_eq!(first_bytes, second_bytes);
}

#[test]
fn snapshot_checksum_detects_payload_corruption() {
    let mut index = SearchIndex::new();
    index
        .add_document(document(
            "one",
            "https://example.org/",
            "A title",
            "some text",
        ))
        .unwrap();
    let mut bytes = Vec::new();
    index.write_snapshot(&mut bytes).unwrap();

    let last = bytes.len() - 1;
    bytes[last] ^= 0x01;

    assert!(matches!(
        SearchIndex::read_snapshot(Cursor::new(bytes)),
        Err(SnapshotError::ChecksumMismatch)
    ));
}

#[test]
fn snapshot_reader_rejects_truncation_and_trailing_bytes() {
    let mut index = SearchIndex::new();
    index
        .add_document(document(
            "one",
            "https://example.org/",
            "A title",
            "some text",
        ))
        .unwrap();
    let mut bytes = Vec::new();
    index.write_snapshot(&mut bytes).unwrap();

    let truncated = bytes[..bytes.len() - 1].to_vec();
    assert!(matches!(
        SearchIndex::read_snapshot(Cursor::new(truncated)),
        Err(SnapshotError::InvalidFormat)
    ));

    bytes.push(0);
    assert!(matches!(
        SearchIndex::read_snapshot(Cursor::new(bytes)),
        Err(SnapshotError::InvalidFormat)
    ));
}

#[test]
fn snapshot_reader_rejects_unknown_format_versions() {
    let mut index = SearchIndex::new();
    index
        .add_document(document(
            "one",
            "https://example.org/",
            "A title",
            "some text",
        ))
        .unwrap();
    let mut bytes = Vec::new();
    index.write_snapshot(&mut bytes).unwrap();

    // The format version immediately follows the eight-byte magic header.
    bytes[8] = 0xff;

    assert!(matches!(
        SearchIndex::read_snapshot(Cursor::new(bytes)),
        Err(SnapshotError::UnsupportedVersion)
    ));
}

use std::io::{self, Cursor, Read};

use hari_search::{Document, SearchIndex, SnapshotError};

const HEADER_SIZE: usize = 32;
const MAX_SNAPSHOT_BYTES: usize = 16 * 1024 * 1024 + 50_000 * 16 + HEADER_SIZE;

fn valid_snapshot() -> Vec<u8> {
    let mut index = SearchIndex::new();
    index
        .add_document(Document {
            id: "alpha".to_owned(),
            url: "https://example.org/alpha".to_owned(),
            title: "Bounded snapshot".to_owned(),
            body: "deterministic hostile input coverage".to_owned(),
        })
        .unwrap();

    let mut bytes = Vec::new();
    index.write_snapshot(&mut bytes).unwrap();
    bytes
}

fn payload_checksum(payload: &[u8]) -> u64 {
    let mut checksum = 0xcbf2_9ce4_8422_2325_u64;
    for byte in payload {
        checksum ^= u64::from(*byte);
        checksum = checksum.wrapping_mul(0x0000_0100_0000_01b3);
    }
    checksum
}

fn refresh_checksum(snapshot: &mut [u8]) {
    let checksum = payload_checksum(&snapshot[HEADER_SIZE..]);
    snapshot[24..32].copy_from_slice(&checksum.to_le_bytes());
}

#[test]
fn rejects_every_truncated_prefix_of_a_valid_snapshot() {
    let snapshot = valid_snapshot();

    for length in 0..snapshot.len() {
        let result = SearchIndex::read_snapshot(Cursor::new(snapshot[..length].to_vec()));
        assert!(
            result.is_err(),
            "accepted a truncated snapshot with {length} bytes"
        );
    }
}

#[test]
fn rejects_single_bit_mutations_across_the_complete_snapshot() {
    let snapshot = valid_snapshot();

    for byte_index in 0..snapshot.len() {
        for bit in 0..u8::BITS {
            let mut mutated = snapshot.clone();
            mutated[byte_index] ^= 1_u8 << bit;

            let result = SearchIndex::read_snapshot(Cursor::new(mutated));
            assert!(
                result.is_err(),
                "accepted a single-bit mutation at byte {byte_index}, bit {bit}"
            );
        }
    }
}

#[test]
fn rejects_excessive_declared_document_counts_before_payload_decoding() {
    let mut snapshot = valid_snapshot();
    snapshot[12..16].copy_from_slice(&u32::MAX.to_le_bytes());

    assert!(matches!(
        SearchIndex::read_snapshot(Cursor::new(snapshot)),
        Err(SnapshotError::TooLarge)
    ));
}

#[test]
fn rejects_payload_lengths_that_do_not_match_the_bounded_input() {
    let mut snapshot = valid_snapshot();
    snapshot[16..24].copy_from_slice(&u64::MAX.to_le_bytes());

    assert!(matches!(
        SearchIndex::read_snapshot(Cursor::new(snapshot)),
        Err(SnapshotError::InvalidFormat)
    ));
}

#[test]
fn rejects_oversized_field_lengths_even_with_a_valid_payload_checksum() {
    let mut snapshot = valid_snapshot();

    // The first payload field is the document ID; its length prefix starts
    // immediately after the fixed-size snapshot header.
    snapshot[HEADER_SIZE..HEADER_SIZE + 4].copy_from_slice(&u32::MAX.to_le_bytes());
    refresh_checksum(&mut snapshot);

    assert!(matches!(
        SearchIndex::read_snapshot(Cursor::new(snapshot)),
        Err(SnapshotError::TooLarge)
    ));
}

#[test]
fn rejects_invalid_utf8_even_when_the_payload_checksum_is_valid() {
    let mut snapshot = valid_snapshot();

    // Corrupt the first byte of the document ID while preserving the checksum.
    snapshot[HEADER_SIZE + 4] = 0xff;
    refresh_checksum(&mut snapshot);

    assert!(matches!(
        SearchIndex::read_snapshot(Cursor::new(snapshot)),
        Err(SnapshotError::InvalidFormat)
    ));
}

struct RepeatingReader {
    bytes_read: usize,
}

impl Read for RepeatingReader {
    fn read(&mut self, buffer: &mut [u8]) -> io::Result<usize> {
        buffer.fill(0);
        self.bytes_read += buffer.len();
        Ok(buffer.len())
    }
}

#[test]
fn caps_stream_consumption_at_the_snapshot_limit_plus_one_byte() {
    let mut reader = RepeatingReader { bytes_read: 0 };

    assert!(matches!(
        SearchIndex::read_snapshot(&mut reader),
        Err(SnapshotError::TooLarge)
    ));
    assert_eq!(MAX_SNAPSHOT_BYTES + 1, reader.bytes_read);
}

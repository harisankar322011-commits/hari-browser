#![no_main]

//! Coverage-guided fuzzing for the bounded HARI Search snapshot decoder.
//!
//! The target exercises both arbitrary byte streams and mutations of a known
//! valid snapshot so the fuzzer can reach deeper field-validation paths.
//! It performs no filesystem or network access.

use std::io::Cursor;

use hari_search::{Document, SearchIndex};
use libfuzzer_sys::fuzz_target;

const MAX_FUZZ_INPUT_BYTES: usize = 16 * 1024;
const SNAPSHOT_HEADER_SIZE: usize = 32;

fn valid_snapshot() -> Vec<u8> {
    let mut index = SearchIndex::new();
    index
        .add_document(Document {
            id: "seed-document".to_owned(),
            url: "https://example.org/fuzz-seed".to_owned(),
            title: "HARI Search snapshot seed".to_owned(),
            body: "bounded snapshot decoder regression corpus".to_owned(),
        })
        .expect("the fixed fuzz seed must satisfy the index limits");

    let mut bytes = Vec::new();
    index
        .write_snapshot(&mut bytes)
        .expect("the fixed fuzz seed must serialize");
    bytes
}

fn refresh_payload_checksum(snapshot: &mut [u8]) {
    if snapshot.len() < SNAPSHOT_HEADER_SIZE {
        return;
    }

    let mut checksum = 0xcbf2_9ce4_8422_2325_u64;
    for byte in &snapshot[SNAPSHOT_HEADER_SIZE..] {
        checksum ^= u64::from(*byte);
        checksum = checksum.wrapping_mul(0x0000_0100_0000_01b3);
    }
    snapshot[24..32].copy_from_slice(&checksum.to_le_bytes());
}

fuzz_target!(|data: &[u8]| {
    if data.len() > MAX_FUZZ_INPUT_BYTES {
        return;
    }

    // First exercise the decoder with completely arbitrary bytes.
    let _ = SearchIndex::read_snapshot(Cursor::new(data));

    // Then mutate a known-good snapshot. Keeping the magic header intact
    // allows coverage-guided mutations to explore version, flags, lengths,
    // checksum, UTF-8, URL validation, and document reconstruction paths.
    let mut candidate = valid_snapshot();
    if candidate.len() > SNAPSHOT_HEADER_SIZE {
        for pair in data.chunks_exact(2).take(128) {
            let span = candidate.len() - SNAPSHOT_HEADER_SIZE;
            let offset = SNAPSHOT_HEADER_SIZE + usize::from(pair[0]) % span;
            candidate[offset] ^= pair[1];
        }
    }

    // Half the inputs retain the original checksum and exercise corruption
    // detection; the other half recompute it to reach deeper payload parsing.
    if data.first().is_some_and(|first| first & 1 == 1) {
        refresh_payload_checksum(&mut candidate);
    }

    let _ = SearchIndex::read_snapshot(Cursor::new(candidate));
});

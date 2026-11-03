//! Versioned, bounded snapshots for the HARI Search in-memory index.
//!
//! Snapshots contain source documents, not derived postings. Loading rebuilds
//! the inverted index through the normal document-validation path. The checksum
//! detects accidental corruption; it is not an authentication mechanism.

use std::collections::BTreeSet;
use std::fmt;
use std::io::{Read, Write};

use super::{
    Document, SearchIndex, MAX_BODY_BYTES, MAX_DOCUMENTS, MAX_ID_BYTES, MAX_INDEX_SOURCE_BYTES,
    MAX_TITLE_BYTES, MAX_URL_BYTES,
};

const SNAPSHOT_MAGIC: &[u8; 8] = b"HARISEA1";
const SNAPSHOT_VERSION: u16 = 1;
const HEADER_SIZE: usize = 32;
const MAX_SNAPSHOT_BYTES: usize = MAX_INDEX_SOURCE_BYTES + MAX_DOCUMENTS * 16 + HEADER_SIZE;

/// Errors returned when a HARI Search snapshot cannot be written or loaded.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum SnapshotError {
    /// Reading from or writing to the supplied stream failed.
    Io,
    /// The snapshot header, field layout, or payload length is invalid.
    InvalidFormat,
    /// The snapshot uses a version this implementation does not support.
    UnsupportedVersion,
    /// The payload checksum does not match the stored checksum.
    ChecksumMismatch,
    /// The snapshot exceeds the configured size or document-count budget.
    TooLarge,
    /// A stored document violates the current indexing rules.
    InvalidDocument,
}

impl fmt::Display for SnapshotError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        let message = match self {
            Self::Io => "snapshot stream I/O failed",
            Self::InvalidFormat => "snapshot format is invalid or incomplete",
            Self::UnsupportedVersion => "snapshot format version is unsupported",
            Self::ChecksumMismatch => "snapshot payload checksum does not match",
            Self::TooLarge => "snapshot exceeds the configured size limit",
            Self::InvalidDocument => "snapshot contains an invalid or duplicate document",
        };
        formatter.write_str(message)
    }
}

impl std::error::Error for SnapshotError {}

impl SearchIndex {
    /// Writes a deterministic, versioned snapshot of the indexed source documents.
    ///
    /// The format stores no derived postings; those are rebuilt and revalidated
    /// by SearchIndex::read_snapshot. The caller controls the destination and
    /// should write to a temporary file and atomically replace the previous file
    /// when filesystem-level crash consistency is required.
    ///
    /// The embedded FNV-1a checksum detects accidental corruption only. It does
    /// not authenticate a snapshot or protect it against deliberate modification.
    pub fn write_snapshot<W: Write>(&self, mut writer: W) -> Result<(), SnapshotError> {
        let mut payload = Vec::with_capacity(
            self.total_source_bytes
                .saturating_add(self.documents.len().saturating_mul(16)),
        );

        for stored in self.documents.values() {
            append_field(&mut payload, &stored.document.id)?;
            append_field(&mut payload, &stored.document.url)?;
            append_field(&mut payload, &stored.document.title)?;
            append_field(&mut payload, &stored.document.body)?;
        }

        let document_count =
            u32::try_from(self.documents.len()).map_err(|_| SnapshotError::TooLarge)?;
        let payload_length = u64::try_from(payload.len()).map_err(|_| SnapshotError::TooLarge)?;
        let checksum = payload_checksum(&payload);

        let mut header = Vec::with_capacity(HEADER_SIZE);
        header.extend_from_slice(SNAPSHOT_MAGIC);
        header.extend_from_slice(&SNAPSHOT_VERSION.to_le_bytes());
        header.extend_from_slice(&0_u16.to_le_bytes());
        header.extend_from_slice(&document_count.to_le_bytes());
        header.extend_from_slice(&payload_length.to_le_bytes());
        header.extend_from_slice(&checksum.to_le_bytes());

        writer.write_all(&header).map_err(|_| SnapshotError::Io)?;
        writer.write_all(&payload).map_err(|_| SnapshotError::Io)?;
        Ok(())
    }

    /// Reads a bounded snapshot and rebuilds its in-memory inverted index.
    ///
    /// The loader rejects unknown versions, malformed lengths, duplicate IDs,
    /// invalid document URLs, excessive document counts, trailing data, and
    /// checksum mismatches. It never fetches a document URL.
    pub fn read_snapshot<R: Read>(reader: R) -> Result<Self, SnapshotError> {
        let read_limit =
            u64::try_from(MAX_SNAPSHOT_BYTES).map_err(|_| SnapshotError::TooLarge)? + 1;
        let mut bytes = Vec::new();
        reader
            .take(read_limit)
            .read_to_end(&mut bytes)
            .map_err(|_| SnapshotError::Io)?;

        if bytes.len() > MAX_SNAPSHOT_BYTES {
            return Err(SnapshotError::TooLarge);
        }
        if bytes.len() < HEADER_SIZE || &bytes[..8] != SNAPSHOT_MAGIC {
            return Err(SnapshotError::InvalidFormat);
        }

        let version = u16::from_le_bytes(
            bytes[8..10]
                .try_into()
                .map_err(|_| SnapshotError::InvalidFormat)?,
        );
        if version != SNAPSHOT_VERSION {
            return Err(SnapshotError::UnsupportedVersion);
        }

        let flags = u16::from_le_bytes(
            bytes[10..12]
                .try_into()
                .map_err(|_| SnapshotError::InvalidFormat)?,
        );
        if flags != 0 {
            return Err(SnapshotError::InvalidFormat);
        }

        let document_count = u32::from_le_bytes(
            bytes[12..16]
                .try_into()
                .map_err(|_| SnapshotError::InvalidFormat)?,
        );
        let document_count =
            usize::try_from(document_count).map_err(|_| SnapshotError::TooLarge)?;
        if document_count > MAX_DOCUMENTS {
            return Err(SnapshotError::TooLarge);
        }

        let payload_length = u64::from_le_bytes(
            bytes[16..24]
                .try_into()
                .map_err(|_| SnapshotError::InvalidFormat)?,
        );
        let payload = &bytes[HEADER_SIZE..];
        let actual_payload_length =
            u64::try_from(payload.len()).map_err(|_| SnapshotError::TooLarge)?;
        if payload_length != actual_payload_length {
            return Err(SnapshotError::InvalidFormat);
        }

        let expected_checksum = u64::from_le_bytes(
            bytes[24..32]
                .try_into()
                .map_err(|_| SnapshotError::InvalidFormat)?,
        );
        if payload_checksum(payload) != expected_checksum {
            return Err(SnapshotError::ChecksumMismatch);
        }

        let mut index = SearchIndex::new();
        let mut document_ids = BTreeSet::new();
        let mut offset = 0_usize;

        for _ in 0..document_count {
            let id = read_field(payload, &mut offset, MAX_ID_BYTES)?;
            if !document_ids.insert(id.clone()) {
                return Err(SnapshotError::InvalidFormat);
            }
            let url = read_field(payload, &mut offset, MAX_URL_BYTES)?;
            let title = read_field(payload, &mut offset, MAX_TITLE_BYTES)?;
            let body = read_field(payload, &mut offset, MAX_BODY_BYTES)?;

            index
                .add_document(Document {
                    id,
                    url,
                    title,
                    body,
                })
                .map_err(|_| SnapshotError::InvalidDocument)?;
        }

        if offset != payload.len() || index.document_count() != document_count {
            return Err(SnapshotError::InvalidFormat);
        }

        Ok(index)
    }
}

fn append_field(output: &mut Vec<u8>, value: &str) -> Result<(), SnapshotError> {
    let length = u32::try_from(value.len()).map_err(|_| SnapshotError::TooLarge)?;
    let maximum_payload = MAX_SNAPSHOT_BYTES - HEADER_SIZE;
    let next_length = output
        .len()
        .checked_add(4)
        .and_then(|length_so_far| length_so_far.checked_add(value.len()))
        .ok_or(SnapshotError::TooLarge)?;
    if next_length > maximum_payload {
        return Err(SnapshotError::TooLarge);
    }

    output.extend_from_slice(&length.to_le_bytes());
    output.extend_from_slice(value.as_bytes());
    Ok(())
}

fn read_field(
    payload: &[u8],
    offset: &mut usize,
    maximum_length: usize,
) -> Result<String, SnapshotError> {
    let length_end = (*offset)
        .checked_add(4)
        .ok_or(SnapshotError::InvalidFormat)?;
    let length_bytes = payload
        .get(*offset..length_end)
        .ok_or(SnapshotError::InvalidFormat)?;
    let length = u32::from_le_bytes(
        length_bytes
            .try_into()
            .map_err(|_| SnapshotError::InvalidFormat)?,
    );
    *offset = length_end;

    let length = usize::try_from(length).map_err(|_| SnapshotError::TooLarge)?;
    if length > maximum_length {
        return Err(SnapshotError::TooLarge);
    }
    let end = (*offset)
        .checked_add(length)
        .ok_or(SnapshotError::InvalidFormat)?;
    let value = payload
        .get(*offset..end)
        .ok_or(SnapshotError::InvalidFormat)?;
    let value = std::str::from_utf8(value).map_err(|_| SnapshotError::InvalidFormat)?;
    *offset = end;
    Ok(value.to_owned())
}

fn payload_checksum(payload: &[u8]) -> u64 {
    let mut checksum = 0xcbf2_9ce4_8422_2325_u64;
    for byte in payload {
        checksum ^= u64::from(*byte);
        checksum = checksum.wrapping_mul(0x0000_0100_0000_01b3);
    }
    checksum
}

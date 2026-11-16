use std::collections::VecDeque;

pub(crate) const MAX_PCM_WRITE_BYTES: usize = 256 * 1024;
pub(crate) const FNV1A64_OFFSET: u64 = 0xcbf29ce484222325;
const FNV1A64_PRIME: u64 = 0x100000001b3;

#[derive(Default)]
pub(crate) struct PcmQueue {
    bytes: VecDeque<u8>,
    frame_bytes: usize,
    capacity_bytes: usize,
}

impl PcmQueue {
    pub(crate) fn configure(
        &mut self,
        frame_bytes: usize,
        capacity_bytes: usize,
    ) -> Result<(), String> {
        if frame_bytes == 0 || capacity_bytes < frame_bytes {
            return Err("PCM queue requires a nonzero frame size and at least one frame of capacity".to_string());
        }
        self.bytes.clear();
        self.frame_bytes = frame_bytes;
        self.capacity_bytes = capacity_bytes / frame_bytes * frame_bytes;
        Ok(())
    }

    pub(crate) fn reset(&mut self) -> usize {
        let discarded_frames = self.queued_frames();
        self.bytes.clear();
        self.frame_bytes = 0;
        self.capacity_bytes = 0;
        discarded_frames
    }

    pub(crate) fn write(&mut self, data: &[u8]) -> Result<usize, String> {
        if self.frame_bytes == 0 {
            return Err("Configure a PCM USB output before writing samples".to_string());
        }
        if data.len() > MAX_PCM_WRITE_BYTES {
            return Err(format!(
                "PCM write exceeds the {}-byte transfer limit",
                MAX_PCM_WRITE_BYTES
            ));
        }
        if !data.len().is_multiple_of(self.frame_bytes) {
            return Err("PCM write length is not aligned to a complete interleaved frame".to_string());
        }
        if data.is_empty() {
            return Ok(0);
        }
        let accepted = data
            .len()
            .min(self.available_bytes())
            / self.frame_bytes
            * self.frame_bytes;
        self.bytes.extend(data[..accepted].iter().copied());
        Ok(accepted)
    }

    pub(crate) fn copy_prefix(
        &self,
        destination: &mut [u8],
        requested_bytes: usize,
    ) -> Result<usize, String> {
        if self.frame_bytes == 0 {
            return Err("Configure a PCM USB output before packetizing samples".to_string());
        }
        if requested_bytes > destination.len()
            || !requested_bytes.is_multiple_of(self.frame_bytes)
        {
            return Err("USB packet buffer length is not aligned to a complete PCM frame".to_string());
        }
        destination[..requested_bytes].fill(0);
        let source_bytes = self.bytes.len().min(requested_bytes)
            / self.frame_bytes
            * self.frame_bytes;
        let (first, second) = self.bytes.as_slices();
        let first_bytes = source_bytes.min(first.len());
        destination[..first_bytes].copy_from_slice(&first[..first_bytes]);
        let second_bytes = source_bytes.saturating_sub(first_bytes);
        if second_bytes > 0 {
            destination[first_bytes..source_bytes].copy_from_slice(&second[..second_bytes]);
        }
        Ok(source_bytes)
    }

    pub(crate) fn consume_prefix(&mut self, byte_count: usize) -> Result<(), String> {
        if self.frame_bytes == 0
            || byte_count > self.bytes.len()
            || !byte_count.is_multiple_of(self.frame_bytes)
        {
            return Err("PCM queue consumption must contain complete queued frames".to_string());
        }
        self.bytes.drain(..byte_count);
        Ok(())
    }

    pub(crate) fn clear(&mut self) -> usize {
        let discarded_frames = self.queued_frames();
        self.bytes.clear();
        discarded_frames
    }

    pub(crate) fn queued_bytes(&self) -> usize {
        self.bytes.len()
    }

    pub(crate) fn queued_frames(&self) -> usize {
        if self.frame_bytes == 0 {
            0
        } else {
            self.bytes.len() / self.frame_bytes
        }
    }

    pub(crate) fn frame_bytes(&self) -> usize {
        self.frame_bytes
    }

    pub(crate) fn available_bytes(&self) -> usize {
        self.capacity_bytes.saturating_sub(self.bytes.len())
    }
}

pub(crate) fn fnv1a64_update(mut hash: u64, data: &[u8]) -> u64 {
    for byte in data {
        hash ^= u64::from(*byte);
        hash = hash.wrapping_mul(FNV1A64_PRIME);
    }
    hash
}

pub(crate) fn fnv1a64(data: &[u8]) -> u64 {
    fnv1a64_update(FNV1A64_OFFSET, data)
}

#[cfg(test)]
mod tests {
    use super::{
        fnv1a64, fnv1a64_update, PcmQueue, FNV1A64_OFFSET, MAX_PCM_WRITE_BYTES,
    };

    fn signed_sample_bytes(value: i64, width: usize) -> Vec<u8> {
        (value as u64).to_le_bytes()[..width].to_vec()
    }

    fn interleaved_fixture(bit_depth: u8) -> Vec<u8> {
        let maximum = (1_i64 << (bit_depth - 1)) - 1;
        let minimum = -(1_i64 << (bit_depth - 1));
        let samples = [
            (0x1234_i64, -0x1234_i64),
            (0, 1),
            (-1, 2),
            (maximum, minimum),
            (minimum, maximum),
            (0x0101, -0x0101),
        ];
        let width = usize::from(bit_depth / 8);
        let mut bytes = Vec::with_capacity(samples.len() * width * 2);
        for (left, right) in samples {
            bytes.extend(signed_sample_bytes(left, width));
            bytes.extend(signed_sample_bytes(right, width));
        }
        bytes
    }

    #[test]
    fn preserves_interleaved_little_endian_samples_for_supported_precisions() {
        for bit_depth in [16, 24, 32] {
            let fixture = interleaved_fixture(bit_depth);
            let frame_bytes = usize::from(bit_depth / 8) * 2;
            let mut queue = PcmQueue::default();
            queue.configure(frame_bytes, fixture.len()).unwrap();
            assert_eq!(queue.write(&fixture).unwrap(), fixture.len());
            let mut packet = vec![0; fixture.len()];
            let packet_len = packet.len();
            let copied = queue.copy_prefix(&mut packet, packet_len).unwrap();
            assert_eq!(copied, fixture.len());
            assert_eq!(packet, fixture);
            assert_eq!(queue.queued_frames(), 6);
        }
    }

    #[test]
    fn applies_bounded_partial_writes_on_complete_frame_boundaries() {
        let mut queue = PcmQueue::default();
        queue.configure(4, 12).unwrap();
        let data: Vec<u8> = (0..24).collect();
        assert_eq!(queue.write(&data).unwrap(), 12);
        assert_eq!(queue.available_bytes(), 0);
        let mut first = [0; 8];
        assert_eq!(queue.copy_prefix(&mut first, 8).unwrap(), 8);
        assert_eq!(first, data[..8]);
        queue.consume_prefix(8).unwrap();
        assert_eq!(queue.write(&data[12..]).unwrap(), 8);
        let mut remaining = [0; 12];
        assert_eq!(queue.copy_prefix(&mut remaining, 12).unwrap(), 12);
        assert_eq!(remaining, data[8..20]);
        assert_eq!(queue.queued_frames(), 3);
    }

    #[test]
    fn reports_empty_partial_and_full_queue_capacity_without_overflow() {
        let mut queue = PcmQueue::default();
        queue.configure(4, 16).unwrap();
        assert_eq!(queue.queued_bytes(), 0);
        assert_eq!(queue.available_bytes(), 16);
        assert_eq!(queue.write(&[1; 8]).unwrap(), 8);
        assert_eq!(queue.queued_bytes(), 8);
        assert_eq!(queue.available_bytes(), 8);
        assert_eq!(queue.write(&[2; 12]).unwrap(), 8);
        assert_eq!(queue.queued_bytes(), 16);
        assert_eq!(queue.available_bytes(), 0);
        assert_eq!(queue.write(&[3; 4]).unwrap(), 0);
        assert_eq!(queue.clear(), 4);
        assert_eq!(queue.queued_bytes(), 0);
        assert_eq!(queue.available_bytes(), 16);
    }

    #[test]
    fn packetizer_zero_fills_only_the_missing_tail_and_counts_no_source_frames() {
        let mut queue = PcmQueue::default();
        queue.configure(4, 32).unwrap();
        let source = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];
        queue.write(&source).unwrap();
        let mut transfer = [0xFF; 20];
        let transfer_len = transfer.len();
        let source_bytes = queue.copy_prefix(&mut transfer, transfer_len).unwrap();
        let source_frames = source_bytes / queue.frame_bytes();
        let silence_frames = transfer.len() / queue.frame_bytes() - source_frames;
        assert_eq!(source_bytes, 12);
        assert_eq!(source_frames, 3);
        assert_eq!(silence_frames, 2);
        assert_eq!(&transfer[..12], &source);
        assert_eq!(&transfer[12..], &[0; 8]);
        assert_eq!(queue.queued_frames(), 3);
    }

    #[test]
    fn preserves_fifo_order_across_vecdeque_wraparound() {
        let mut queue = PcmQueue::default();
        queue.configure(2, 12).unwrap();
        let first: Vec<u8> = (0..12).collect();
        queue.write(&first).unwrap();
        queue.consume_prefix(8).unwrap();
        let second = [12, 13, 14, 15, 16, 17, 18, 19];
        assert_eq!(queue.write(&second).unwrap(), 8);
        let mut output = [0; 12];
        assert_eq!(queue.copy_prefix(&mut output, 12).unwrap(), 12);
        assert_eq!(output, [8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19]);
    }

    #[test]
    fn rejects_incomplete_frames_and_accepts_empty_writes_as_no_ops() {
        let mut queue = PcmQueue::default();
        queue.configure(4, 32).unwrap();
        assert!(queue.write(&[1, 2, 3, 4, 5]).is_err());
        assert_eq!(queue.write(&[]).unwrap(), 0);
        assert_eq!(queue.queued_bytes(), 0);
    }

    #[test]
    fn rejects_writes_larger_than_the_public_limit() {
        let mut queue = PcmQueue::default();
        queue.configure(4, MAX_PCM_WRITE_BYTES + 4).unwrap();
        let oversized = vec![0x5A; MAX_PCM_WRITE_BYTES + 4];
        assert!(queue.write(&oversized).is_err());
    }

    #[test]
    fn clear_reports_discarded_source_frames() {
        let mut queue = PcmQueue::default();
        queue.configure(6, 30).unwrap();
        queue.write(&[1; 24]).unwrap();
        assert_eq!(queue.clear(), 4);
        assert_eq!(queue.queued_bytes(), 0);
    }

    #[test]
    fn incremental_payload_hash_matches_single_pass_hash() {
        let data = interleaved_fixture(24);
        let split = data.len() / 2;
        let hash = fnv1a64_update(
            fnv1a64_update(FNV1A64_OFFSET, &data[..split]),
            &data[split..],
        );
        assert_eq!(hash, fnv1a64(&data));
    }
}

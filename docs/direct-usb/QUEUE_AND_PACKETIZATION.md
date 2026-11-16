# Rust PCM Queue and USB Packetization

The producer/queue/transport contract is in
[DIRECT_USB_PIPELINE.md](DIRECT_USB_PIPELINE.md). This page records the
queue's concrete behavior and invariants.

## 1. The queue

`rust/direct-usb/src/pcm.rs` implements `PcmQueue`, a bounded
`VecDeque<u8>` byte FIFO. It is not a decoder, a typed PCM adapter, a
resampler, a timestamp store or a playback-position clock.

- maximum public write request: 256 KiB (`MAX_PCM_WRITE_BYTES`);
- capacity target: approximately 200 ms (`sample_rate * frame_bytes / 5`
  at session configuration);
- capacity clamp: 16 KiB minimum and 1 MiB maximum;
- capacity rounded down to complete PCM frames;
- `frame_bytes = channels * subslot_bytes`;
- writes and consumption are complete interleaved frames only;
- a write can accept fewer bytes than requested; the accepted byte count
  is authoritative;
- `available_bytes` is advisory and can change before the next write;
- no accepted byte is silently dropped.

The queue stores bytes; format, generation and timestamp metadata are
owned by the playback engine and session around it.

## 2. Decoder producer contract

The Symphonia decoder producer runs in Rust, separate from the USB
worker:

1. decode bounded blocks;
2. preserve the decoder's sample metadata;
3. convert through the exact PCM adapter;
4. construct complete interleaved frames for the negotiated UAC format;
5. split into chunks no larger than 256 KiB;
6. query available capacity as a hint only;
7. write, and treat the returned accepted byte count as the only truth;
8. retain the unaccepted suffix exactly;
9. wait for a queue-space notification or a bounded retry delay before
   retrying;
10. stop on generation change, pause/seek/stop or a fatal source/output
    error.

The producer's accepted-frame count never advances for unaccepted bytes,
and it never busy-spins on a full queue or re-copies a chunk that was
already rejected for capacity.

## 3. Startup and steady-state buffering

The USB worker starts after the producer reaches the startup watermark:
min(capacity / 2, sample rate / 5) frames queued, or the whole source when
it ends before the watermark. The queue stays bounded during playback.

For each service interval, the packetizer calculates the required frame
count from the active clock and endpoint schedule, then copies complete
source frames into stable transfer memory. `copy_prefix` zero-initializes
the packet tail and returns the number of source bytes actually copied;
only that count is consumed from the queue — the zero-filled tail was
never queued and is not consumed as source data. Copied source frames and
silence frames are counted separately. Silence is not source media and is
not position progress.

## 4. Transfer and packet accounting

Decoded, queue-accepted, submitted, in-flight, completed,
flushed/discarded source frames and silence frames are counted
separately. Queue bytes, OUT transfer counts, feedback counts and bytes
written are never used as playback position.

A transfer advances completed source position only after a successful
callback. If a transfer's status or length is invalid, the whole packet is
not credited; per-transfer metadata records which submitted frames were
source frames and which were silence.

## 5. Flush barrier

A flush closes admission for the old generation, blocks further queue
writes, stops new OUT/feedback submissions, cancels pending transfers,
keeps libusb event processing running until callbacks drain, clears the
old queue, reconciles submitted/in-flight/completed counters and resets
packet-clock fractional state where required. No old-generation frame is
submitted after the barrier completes.

## 6. Tests

The queue's Rust tests cover byte FIFO behavior, 16/24/32-bit interleaved
payload patterns, full-frame partial writes, capacity reporting and
clamping, wraparound ordering, the incremental payload hash and
silence-tail zero filling. The playback engine's tests cover the producer
side end to end against a fake sink: exact PCM delivery to EOS,
pause/resume continuity and seek correctness.

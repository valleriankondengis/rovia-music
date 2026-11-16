# Exact PCM Adapter

## 1. Location and contract

The adapter is the Rust stage between Symphonia decoded frames and the
selected output format. It is shared by all three Bit-Perfect modes:
Direct USB and DAP feed their bounded queues through it, and Native A14's
pull source uses it to produce the AudioTrack's blocks. It is not a
Media3 AudioSink, renderer callback, Kotlin PCM bridge or decoder. See
[DIRECT_USB_PIPELINE.md](../direct-usb/DIRECT_USB_PIPELINE.md) for the
Direct USB path.

Input is a typed decoded-frame block. Output is a complete-frame PCM
block matching the negotiated output format (a descriptor-selected UAC
alternate for Direct USB, the AAudio width for DAP, the AudioTrack width
for Native A14). Decoded metadata stays separate from output format
metadata.

## 2. Negotiation

Negotiate in this order:

1. Read actual decoder output sample type, precision, channels/order and sample rate.
2. Match sample rate exactly to a compatible UAC clock/alternate; Direct USB does not resample to make a device fit.
3. Match channel count/layout to a supported UAC alternate without mixing or substitution.
4. Choose an advertised UAC bit resolution that preserves all meaningful lossless source bits.
5. Record the subslot width separately from UAC bit resolution.
6. Choose a byte layout/endian conversion that matches UAC Type I payload expectations.
7. Reject the source/device combination if any required property cannot be preserved.

## 3. Permitted lossless operations

- Exact signed-integer repacking between documented memory/subslot widths.
- Exact sign extension or padding that preserves numerical sample value and valid-bit position.
- Exact byte-order serialization to the USB payload order.
- Planar/interleaved conversion that preserves channel identity, order and every sample.
- Explicit unsigned PCM centering where required by a supported source representation, with sample-exact vectors.

Every operation requires boundary-vector, random-vector and complete-frame tests.

## 4. Floating-point output policy

Do not route integer source samples through Float32 as a generic bridge. For floating-point decoder output:

- For a lossless source, preserve the floating sample representation or prove each output integer sample is mathematically exact. Otherwise reject a sample-exact Bit-Perfect route.
- For a lossy source, permit only a documented deterministic mapping into the selected integer UAC format, validating finite values and bounds. Label this direct decoded-PCM output, not bit-identical pre-encode audio.
- Do not silently clip, round, dither, resample, apply gain or normalize.

The conversion algorithm, scaling, endpoint convention and error
handling are pinned in `rust/direct-usb/src/adapter.rs` and its tests.

## 5. Prohibited operations for lossless Bit-Perfect

No sample-rate conversion, bit-depth truncation, unproven Float32 bridge, gain, normalization, ReplayGain, EQ, mixing, channel remapping, dither, clipping, fades, crossfades, time stretch or pitch shift. Do not widen samples after information has already been truncated and claim restored precision.

## 6. Validation

Compare input decoded samples and adapter output samples at sample level for minimum, maximum, zero, positive/negative boundaries and deterministic randomized vectors. Verify endianness, signedness, sign extension, alignment, packed 24-bit, 24-in-32, interleaving, channel order, exact frame count and timestamps. Test each Symphonia sample representation that Rovia enables.

# Rovia Bit-Perfect and Direct Decoded-PCM Contract

## 1. Terminology

**Lossless Bit-Perfect** means the source is lossless, the selected decoder reconstructs its samples, the PCM adapter preserves each meaningful sample bit, and the complete output route does not alter sample values, sample rate, channel identity or ordering.

**Direct decoded-PCM output** means a lossy source has been decoded and its resulting PCM is delivered without additional Rovia DSP or resampling. It is not bit-identical to PCM before lossy encoding and must not be marketed as pre-encode Bit-Perfect.

A successful decoder, a supported sample-rate label or a successful USB transfer alone is not proof of either end-to-end property.

## 2. Direct USB requirements

- Symphonia is the only Direct USB decoder for enabled and verified supported streams.
- WAV/RIFF uses Symphonia Wave demuxing and PCM decoding.
- PCM adapter preserves lossless samples exactly; a non-exact mapping is rejected for a lossless Bit-Perfect claim.
- The queue is bounded and frame-aligned; partial writes are retained and retried without reordering or loss.
- USB sample rate and UAC resolution/subslot are negotiated from descriptors and verified by clock read-back.
- No software gain, resampling, EQ, mix, dither, truncation, normalization or undocumented conversion is allowed for lossless Bit-Perfect.
- Position advances from successfully completed source frames, not decoder read-ahead or queue acceptance.
- A stale generation cannot produce frames after seek/flush.
- There is no silent Android Codec2, phone speaker or alternate-output fallback.

## 3. Native A14 and DAP requirements

Native A14 and DAP decode with the same shared Symphonia engine in Rust
as Direct USB and are bound by the same sample-preservation contract:
the adapter mapping is exact, no processing is applied, and position
advances from the output's own completed frames (the AudioTrack playback
head for Native A14, AAudio frames-read for DAP). They differ from Direct
USB only in the output route — the Android framework/HAL mixer route for
Native A14, an AAudio stream for DAP — and each mode's behavior is
described in its own document under [modes/](../modes/README.md).

## 4. OFF

OFF retains Android Codec2/platform decoding by design. It is the
non-Bit-Perfect mode and is not bound by this contract; its measured
behavior on the test device is recorded in
[verification/](../verification/README.md).

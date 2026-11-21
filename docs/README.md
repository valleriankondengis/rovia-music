# Rovia Documentation

Technical documentation for Rovia's playback implementation: how Bit-Perfect
playback is built, how Media3 and ExoPlayer coordinate it, how the Direct USB
backend works, and why the Bit-Perfect modes decode with Symphonia.

## Start here

1. [Architecture](ARCHITECTURE.md) — the playback architecture and the four output modes.
2. [Feature reference](FEATURES.md) — every user-facing feature of the app and how it behaves.
3. [Repository structure](STRUCTURE.md) — source and documentation map.
4. [Official references](REFERENCE.md) — platform, Media3, Symphonia, UAC and libusb sources.

## Technical areas

- [Media3 and ExoPlayer](media3/README.md) — the player/session framework and the decoder policy.
- [Decoder module](decoder/DECODER_MODULE.md) — the replaceable decoder backend (Symphonia, id `symphonia-rust`) and how to add or replace a decoder.
- [Direct USB native playback](direct-usb/README.md) — the Rust USB backend end to end.
- [PCM integrity](pcm/README.md) — how decoded samples are preserved.
- [Output modes](modes/README.md) — OFF, Native A14, Direct USB and DAP.
- [Room3 database](room3/README.md) — the Room 3 tables, the database mechanism, and the features that depend on it.
- [Verification](verification/README.md) — measurements and validation records.
- [Hardware](hardware/README.md) — the test phone, the DAC and their validated combination.

## Decoder policy at a glance

Every mode — OFF, Direct USB, Native A14 and DAP — decodes with
Symphonia in Rust (backend id `symphonia-rust`), for lossless and lossy
codecs alike, so the decoder never changes when a queue moves between
lossless and lossy tracks or when the output mode changes. OFF, the
non-Bit-Perfect mode, sends the same decoded PCM through the Android
system mixer instead of a verified bit-perfect route. The reason the
platform decoder was removed entirely (2026-11-21) is measured, not
assumed: on the test device the old platform path truncated 24-bit
sources to 16-bit PCM before the output (see
[Verification](verification/README.md), archived measurement), which is
exactly what the Bit-Perfect modes exist to avoid. HE-AAC was sacrificed
in the same change — Symphonia has no SBR decoder — and a future backend
can add it via the decoder registry.

## Direct USB architecture rule

Direct USB does not use a custom Media3 AudioSink, a ForwardingAudioSink or
renderer PCM forwarding as a bridge to Rust. Symphonia decodes the original
source inside Rust, the Rust PCM pipeline feeds the Rust UAC transport, and
a dedicated control renderer plus coordinator connects the native engine to
the Media3 player and MediaSession. The end-to-end contract is in
[the Direct USB pipeline document](direct-usb/DIRECT_USB_PIPELINE.md).

# Symphonia Native Playback Engine

## 1. Scope

Symphonia is the decoder engine for every mode — Bit-Perfect modes
and standard (OFF) playback alike, since the Symphonia-only change of
2026-11-21. It decodes every enabled lossless and lossy
codec/container combination; WAV/RIFF is handled by Symphonia's Wave
demuxer with its PCM decoder, and Android `MediaExtractor` plays no
part in any playback path. The backend's canonical id is
`symphonia-rust` (see [../decoder/DECODER_MODULE.md](../decoder/DECODER_MODULE.md)
for the `<decoder>-<language/tech>` naming rule).

The decoder core is independent of USB, UAC descriptors, libusb, Android
UI and Media3. Its output is a typed decoded-frame stream consumed by the
Rust PCM adapter and, depending on the mode, the bounded queue (Direct USB
and DAP), the block pull source (Native A14), or the standard AudioTrack
pump (OFF mode, via `NativePcmSource`). Decoder and transport work
run on separate threads; no PCM block passes through Kotlin in Direct USB
or DAP mode.

## 1a. Backend trait + registry (2026-11-21)

The decoder is a replaceable backend, not a hardcoded library. On the
Rust side, `rust/decoder/src/backend.rs` defines the `DecoderBackend`
trait (`open_path`, `decode_next`, `seek`, `info`) and the
`BackendRegistry`, which resolves a codec name to a backend id and
creates backends by id. `SymphoniaBackend` is the trait implementation
wrapping the existing `DecoderEngine`; the crate stays `rovia-decoder`
and `DecoderEngine` keeps its name. On the Kotlin side, the mirror is
`playback/decoder/DecoderBackend.kt` (interface +
`DecodedSourceInfo`) and `DecoderRegistry.kt` (the single registration
point). Playback code never names Symphonia directly except the
registry default and the backend file itself.

To add a decoder: implement the trait (Rust) and/or the interface
(Kotlin), register it in the registry, optionally make it the
default. To replace Symphonia entirely: register a different default
(e.g. `ffmpeg-c`) and delete the Symphonia backend. No coordinator,
service or UI file changes. Full instructions:
[../decoder/DECODER_MODULE.md](../decoder/DECODER_MODULE.md).

The engine lives in `rust/decoder` and is used by the playback engine in
the `rovia-uniffi` crate, which is also where the exact PCM adapter and
the producer live.

## 2. Reader, probe and demux lifecycle

The Rust source reader wraps an owned, duplicated, seekable media
descriptor behind the `MediaSourceStream` abstraction Symphonia reads
from. Short reads, EOF, seek, descriptor invalidation, cancellation and
close are all handled deterministically.

The decode sequence is:

1. Create the source reader from the duplicated descriptor.
2. Probe the container format with Symphonia.
3. Open the format reader/demuxer.
4. Inspect tracks and select a supported audio stream.
5. Resolve the actual codec/profile from the stream parameters.
6. Create that codec's decoder.
7. Read packets for the selected stream and decode them into typed audio
   buffers.
8. Preserve timestamps/time base, frame counts, channel order and the
   decoder output representation.
9. Drain the decoder at EOS and reset it on seek.

The decoder is never picked from a filename extension, and a recognized
container does not imply every codec inside it is supported.

## 3. Enabled formats

Symphonia is pinned by Git revision (0.6.1 series) with only these
features enabled:

| Container / demuxer | Feature | Source examples |
|---|---|---|
| Wave / RIFF | `wav` | WAV with supported PCM encodings |
| FLAC | `flac` | FLAC |
| AIFF | `aiff` | AIFF |
| CAF | `caf` | CAF |
| ISO/MP4 | `isomp4` | M4A / MP4 |
| OGG | `ogg` | OGG |
| Matroska / WebM | `mkv` | MKV / WebM audio tracks |
| APE (container probing) | `ape` | APE container/metadata only — see below |

| Decoder | Feature | Notes |
|---|---|---|
| PCM | `pcm` | Includes supported Wave/RIFF PCM variants |
| FLAC | `flac` | Lossless |
| ALAC | `alac` | Lossless; M4A/MP4 also needs `isomp4` |
| AAC-LC | `aac` | Lossy; M4A/MP4 also needs `isomp4` |
| MP1 / MP2 / MP3 | `mpa` | MPEG audio |
| Vorbis | `vorbis` | Lossy, commonly OGG |
| ADPCM | `adpcm` | Only supported profiles |

WAV is a container; M4A/MP4 may contain ALAC or AAC-LC. The stream codec
parameters decide, not the extension.

Symphonia 0.6.1 provides no decoder for DSD, WavPack, APE audio, WMA, Opus
or HE-AAC/HE-AACv2, so those formats fail explicitly in every mode (an
APE file's container can be probed, but its audio cannot be decoded).
There is no fallback to a platform decoder in any mode, and an
unsupported item is never silently skipped — playback stops on it and
the reason is published for the UI to show. HE-AAC was sacrificed
deliberately on 2026-11-21 for the multi-platform Symphonia plan; a
future backend registered as e.g. `ffmpeg-c` can add it without
migrating the project.

## 4. Decoded-frame contract

Each decoded block carries its sample rate, channel layout/order, actual
decoded sample type, valid precision and container width, signedness, byte
order, planar/interleaved layout, frame count, timestamp and time base.
These properties are never erased by reducing a block to a bare byte
vector without format state.

Integer sources are never routed through Float32 as a generic bridge.
Floating output stays floating until the adapter's conversion policy
applies. A lossless source supports a sample-exact claim only when the
decoded sequence is preserved exactly end to end; lossy output is direct
decoded PCM, not identity with pre-encode source PCM. FLAC and ALAC may
arrive left-justified in a wider container with fewer valid bits; the
adapter normalizes using the valid-bits count, so the meaningful samples
are preserved exactly.

## 5. Decoder lifecycle and synchronization

The playback engine drives the decoder through open, prepare, play,
pause, seek, stop and release, tracking:

- the selected container, stream and codec/profile;
- the current sample rate, layout and sample representation;
- packet and decoded-frame timestamps/time base;
- seek landing: the demuxer seeks accurately, the decoder resets, and the
  producer trims by block timestamp so playback resumes at the requested
  position without double-discarding preroll;
- recoverable packet errors (skipped) versus fatal errors (fail the
  generation);
- the generation ID and cancellation state;
- EOS/drain completion, kept separate from output completion.

A stale generation never writes into the queue after a seek, stop or item
change, and USB callbacks never call into decoder state.

## 6. Tests

The decoder and playback engine are covered by Rust host tests:

- FLAC, WAV and the other enabled container/codec pairs decode to the
  expected frame counts and sample values;
- lossless output is compared sample-by-sample against the source PCM;
- seek lands at the requested position with the exact remaining frames,
  for files with and without a seek table, at 16-bit and 24-bit;
- pause flushes and resumes without losing or duplicating frames;
- a seek issued while playing delivers exactly the requested suffix;
- truncated, corrupt and unsupported inputs fail with errors, not
  panics;
- the exact PCM adapter is tested at boundary values (minimum, maximum,
  zero, sign extension, 24-in-32 alignment, left-justified samples,
  float exactness and rejection).

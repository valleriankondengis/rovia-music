# Decoder Policy — Symphonia Only

> Changed 2026-11-21 (locked by Vall): Rovia decodes with ONE decoder
> backend in EVERY mode. The Android platform decoder (Codec2 /
> `c2.android.*`) is gone from all playback paths, including OFF.
> The historical measurement that motivated the split is archived at
> [../verification/archive/result_c2_android_flac_decoder.md](../verification/archive/result_c2_android_flac_decoder.md)
> — it is history, not a current component.

## 1. The policy

| Mode | Decoder | Output backend |
|---|---|---|
| OFF (standard) | Symphonia, id `symphonia-rust` | Decoded PCM pumped into a plain AudioTrack (media usage); mixed by the Android audio system |
| Native A14 | Symphonia, id `symphonia-rust` | Android Native Bit-Perfect framework/HAL route, fed by an application-owned AudioTrack |
| Direct USB | Symphonia, id `symphonia-rust` | Rust PCM pipeline and Rust/libusb UAC transport |
| DAP Mode | Symphonia, id `symphonia-rust` | AAudio stream owned by the Rust session |

One decoder for all modes, for lossless and lossy sources alike, so
the decoder never changes when a queue moves between lossless and
lossy tracks, or when the output mode changes. The only thing that
changes between modes is the output route.

The decoder is a replaceable backend, not a hardcoded library:
Kotlin playback code talks only to the `DecoderBackend` interface and
resolves backends through `DecoderRegistry`; Rust playback code talks
only to the `DecoderBackend` trait and `BackendRegistry` in
`rust/decoder/src/backend.rs`. See
[../decoder/DECODER_MODULE.md](../decoder/DECODER_MODULE.md).

Backend ids follow the `<decoder>-<language/tech>` rule (locked
2026-11-21): the Symphonia backend is `symphonia-rust`, a future
FFmpeg backend would be `ffmpeg-c`. Never use a bare decoder name
such as `symphonia` as an id.

The player shows the decoded source (decoder id, codec, sample rate,
bit depth) from `PlaybackDecoderStore` in its audio pipeline view.
It never shows a platform decoder name.

## 2. Why no platform decoder

This is measured, not assumed: on the test device, 24-bit FLAC
sources decoded by the old platform decoder reached the Android audio
output as 16-bit PCM, and the standard output route resamples to the
system rates. Those measurements are kept in the archive above.
Symphonia's decoded output, by contrast, is a known sample sequence
under Rovia's control, identical across all backends. The platform
decoder was therefore removed entirely rather than kept for OFF —
keeping it would have meant two different decoders, two different
sample sequences, and a silent quality split between modes.

ExoPlayer / MediaSession remain in the app, but only as queue and
timeline drivers: they own the media items, the position clock and
the notification. They decode nothing. `RoviaRenderersFactory`
never builds a platform audio renderer.

## 3. Symphonia capability baseline

Rovia uses Symphonia 0.6.1 (Git dependency pinned by exact revision
`5f879df0bf309246a48d57caae457fd8c0e2a5cb`; not from crates.io, not
vendored), with only the features the app needs enabled:

### Containers / demuxers

| Container / demuxer | Feature | Example source |
|---|---|---|
| Wave / RIFF | `wav` | WAV |
| FLAC | `flac` | FLAC |
| AIFF | `aiff` | AIFF |
| CAF | `caf` | CAF |
| ISO/MP4 | `isomp4` | M4A / MP4 |
| MKV / WebM | `mkv` | Matroska / WebM |
| OGG | `ogg` | OGG |
| APE container probing | `ape` | Container/metadata only; no APE audio decoder |

### Decoder codecs

| Codec | Feature | Source type / note |
|---|---|---|
| PCM | `pcm` | PCM carried by supported containers such as Wave and AIFF |
| FLAC | `flac` | Lossless |
| ALAC | `alac` | Lossless; M4A/MP4 also needs `isomp4` |
| MP3 | `mpa` | Lossy |
| AAC-LC | `aac` | Lossy; common M4A/MP4 path also needs `isomp4` |
| Vorbis | `vorbis` | Lossy; commonly paired with OGG |
| ADPCM | `adpcm` | Supported ADPCM profiles only |
| MP1 / MP2 | `mpa` | Legacy MPEG audio |

WAV is a container, not a codec. M4A/MP4 is also a container and may
contain ALAC or AAC-LC; the decoder is chosen from the selected
stream's codec parameters, not the file extension. Container support
and codec support are separate facts: a container can carry a codec
for which no decoder is enabled. Gapless behavior requires support
from both the container demuxer and the decoder.

## 4. Explicitly unsupported — no fallback, no silent skip

Symphonia 0.6.1 provides no decoder for these, so they fail
explicitly in EVERY mode (there is no platform fallback to silently
switch to, and an unsupported item is never removed from the queue
and skipped — playback stops on it and the reason is published to
`PlaybackDecoderStore` for the UI to show):

| Format | Reason |
|---|---|
| HE-AAC / HE-AACv2 | No SBR decoder in Symphonia. **Sacrificed 2026-11-21** (locked by Vall): Rovia is going multi-platform on Symphonia, and HE-AAC is the price. A future backend (e.g. `ffmpeg-c`) can add it via the registry without migrating the project. |
| AAC-ELD, xHE-AAC | No decoder in Symphonia |
| Opus (incl. Opus in MP4) | No Opus decoder enabled in this build |
| DSD | No decoder in Symphonia |
| WavPack, APE audio | No decoder in Symphonia (APE container can be probed, its audio cannot be decoded) |
| WMA, AC-3 / E-AC-3 | No decoder in Symphonia |

Container metadata parsing for such a track does not imply decode
support. The Rust `BackendRegistry::resolve(codec)` and the Kotlin
`DecoderRegistry.resolve(container, codec)` both return the
unsupported reason naming the codec, so the failure message is
specific instead of a generic "not supported".

Symphonia is distributed under MPL-2.0. Its source is not copied or
vendored into Rovia.

## 5. PCM representation

A decoder's integer/float output is never assumed from the file
extension or source metadata. Float32 is not a universal lossless
representation for 32-bit integer samples. Decoded values are
preserved as they are; truncated data is never widened and presented
as restored precision. The format model is described in
[PCM_FORMAT_MODEL.md](../pcm/PCM_FORMAT_MODEL.md).

For WAV/RIFF, all paths use Symphonia's Wave demuxer and PCM decoder;
Android MediaExtractor is not used for WAV in any mode. For lossless
sources, sample-exact decoding supports an end-to-end Bit-Perfect
claim once the output route is verified. For lossy sources, the
decoded PCM is delivered without additional Rovia resampling or DSP,
but it is not identical to the original pre-encode samples — that is
direct decoded-PCM output, not lossless Bit-Perfect output. OFF mode
additionally goes through the Android system mixer, so OFF is never
labelled bit-perfect even for lossless sources.

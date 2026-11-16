# Decoder Policy by Output Mode

## 1. The policy

| Mode | Decoder | Output backend |
|---|---|---|
| OFF | Android Codec2/platform decoder selected for the source format and device | Standard Media3/Android audio output |
| Native A14 | Symphonia in Rust | Android Native Bit-Perfect framework/HAL route, fed by an application-owned AudioTrack |
| Direct USB | Symphonia in Rust | Rust PCM pipeline and Rust/libusb UAC transport |
| DAP Mode | Symphonia in Rust | AAudio stream owned by the Rust session |

Every Bit-Perfect mode uses Symphonia, for lossless and lossy source
formats alike, so the decoder never changes when a queue moves between
lossless and lossy tracks. Codec2/platform decoders serve OFF only.

Codec2 is a platform framework, not one codec. `c2.android.flac.decoder`
is specifically the FLAC decoder component of the OFF path; MP3, AAC and
other formats use their corresponding platform decoder components there.
The player shows the actual component name for the current track in its
audio pipeline view.

Why the Bit-Perfect modes do not use the platform decoders is measured,
not assumed: on the test device, 24-bit FLAC sources decoded by
`c2.android.flac.decoder` reach the Android audio output as 16-bit PCM,
and the standard output route resamples to the system rates. Those
measurements are kept in [Verification](../verification/README.md).
Symphonia's decoded output, by contrast, is a known sample sequence under
Rovia's control, identical across all three Bit-Perfect backends.

For WAV/RIFF, the Bit-Perfect paths use Symphonia's Wave demuxer and PCM
decoder; Android MediaExtractor is not used for WAV in a Bit-Perfect mode.
Android Codec2 never decodes the same item as a Bit-Perfect engine, and
there is no silent fallback to Android playback when Symphonia cannot
decode a source: the item fails in that mode and remains playable in OFF.

Decoded PCM stays inside Rust for Direct USB and DAP; for Native A14 it
moves as whole blocks from the Rust pull source to the mode's own
AudioTrack. It is never forwarded through a Media3 AudioSink. For lossless
sources, sample-exact decoding supports an end-to-end Bit-Perfect claim
once the output route is verified. For lossy sources, the decoded PCM is
delivered without additional Rovia resampling or DSP, but it is not
identical to the original pre-encode samples — that is direct decoded-PCM
output, not lossless Bit-Perfect output.

## 2. Symphonia capability baseline

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
contain ALAC or AAC-LC; the decoder is chosen from the selected stream's
codec parameters, not the file extension. Container support and codec
support are separate facts: a container can carry a codec for which no
decoder is enabled. Gapless behavior requires support from both the
container demuxer and the decoder.

Symphonia 0.6.1 does not list DSD, WavPack, APE audio decoding, WMA, Opus
decoding, HE-AAC or HE-AACv2 as supported decoders. Those formats are
unsupported in the Bit-Perfect paths; container metadata parsing for such
a track does not imply decode support.

Symphonia is distributed under MPL-2.0. Its source is not copied or
vendored into Rovia.

## 3. PCM representation

A decoder's integer/float output is never assumed from the file extension
or source metadata. Float32 is not a universal lossless representation
for 32-bit integer samples. Decoded values are preserved as they are;
truncated data is never widened and presented as restored precision. The
format model is described in [PCM_FORMAT_MODEL.md](../pcm/PCM_FORMAT_MODEL.md).

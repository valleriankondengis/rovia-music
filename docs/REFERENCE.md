# Official Technical References

This index lists the official platform, framework, protocol and dependency sources Rovia's implementation is based on.

## Android platform and Codec2

| Topic | Source | Use in Rovia |
|---|---|---|
| Preferred mixer attributes and optional bit-perfect USB behavior | [AOSP preferred mixer attributes](https://source.android.com/docs/core/audio/preferred-mixer-attr) | Native A14; HAL requirements and route validation |
| Mixer attributes API | [Android `AudioMixerAttributes`](https://developer.android.com/reference/android/media/AudioMixerAttributes) | `MIXER_BEHAVIOR_BIT_PERFECT` |
| Audio manager API | [Android `AudioManager`](https://developer.android.com/reference/android/media/AudioManager) | Preferred mixer attributes and route queries |
| Android USB host | [Android USB host guide](https://developer.android.com/develop/connectivity/usb/host) | USB permissions and connection APIs |
| Codec2 overview | [AOSP Codec 2.0](https://source.android.com/docs/core/media/codec2) | Android platform codec framework |
| MediaCodec API | [Android `MediaCodec`](https://developer.android.com/reference/android/media/MediaCodec) | Platform decoder identity and output format |
| FLAC Codec2 component | [AOSP `C2SoftFlacDec.cpp`](https://android.googlesource.com/platform/frameworks/av/+/refs/heads/main/media/codec2/components/flac/C2SoftFlacDec.cpp) | FLAC-specific platform component; verify target-device source/build |

## AndroidX Media3 and ExoPlayer

| Topic | Source | Use in Rovia |
|---|---|---|
| Media3 release history | [AndroidX Media3 releases](https://developer.android.com/jetpack/androidx/releases/media3) | Version and migration notes |
| ExoPlayer customization | [Media3 ExoPlayer customization](https://developer.android.com/media/media3/exoplayer/customization) | Official component extension guidance |
| ExoPlayer API | [AndroidX `ExoPlayer`](https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer) | Player/renderer architecture |
| Player API | [AndroidX `Player`](https://developer.android.com/reference/androidx/media3/common/Player) | App-facing player contract |
| MediaSession API | [AndroidX `MediaSession`](https://developer.android.com/reference/androidx/media3/session/MediaSession) | Session/player integration |
| Renderer API | [AndroidX `Renderer`](https://developer.android.com/reference/androidx/media3/exoplayer/Renderer) | Renderer lifecycle and integration proof |
| Renderer media clock | [AndroidX `Renderer.getMediaClock()`](https://developer.android.com/reference/androidx/media3/exoplayer/Renderer#getMediaClock()) | Verify whether the native output clock can serve as ExoPlayer's single playback clock |
| RenderersFactory API | [AndroidX `RenderersFactory`](https://developer.android.com/reference/androidx/media3/exoplayer/RenderersFactory) | Renderer construction |
| DefaultRenderersFactory API | [AndroidX `DefaultRenderersFactory`](https://developer.android.com/reference/androidx/media3/exoplayer/DefaultRenderersFactory) | Standard renderer behavior |
| Media3 source | [AndroidX Media repository](https://github.com/androidx/media) | Inspect exact tag/commit matching resolved artifacts |

Rovia Direct USB must not use `AudioSink` or `ForwardingAudioSink` as the Symphonia/USB integration boundary. The references above are for Media3 player/session/renderer coordination and standard Android-decoded modes.

## Symphonia

| Topic | Source | Use in Rovia |
|---|---|---|
| Symphonia 0.6.1 crate documentation | [Versioned Symphonia documentation](https://docs.rs/symphonia/0.6.1/symphonia/) | Current planning baseline for supported containers, codec decoders, feature flags and gapless capability |
| Symphonia getting started | [Decoder and demuxer workflow](https://github.com/pdeljanov/Symphonia/blob/main/GETTING_STARTED.md) | Probe, format reader, packet and decoder lifecycle; re-check against the pinned tag |
| Symphonia repository | [Symphonia source repository](https://github.com/pdeljanov/Symphonia) | Versioned source, feature declarations, license and notices |
| Symphonia 0.6.1 license | [MPL-2.0](https://github.com/pdeljanov/Symphonia/blob/v0.6.1/LICENSE) | Third-party dependency license; Rovia-authored code licensing is a separate decision |
| Symphonia releases | [Release history](https://github.com/pdeljanov/Symphonia/releases) | Verify the newest stable release and migration changes before pinning Cargo dependencies |

Rovia pins Symphonia 0.6.1 by Git revision in `rust/decoder/Cargo.toml`, with only the demuxer/codec features it uses enabled; the enabled set is listed in [the decoder policy](media3/DECODER_POLICY.md). Default features do not include every audio codec.

## USB Audio Class and libusb

| Topic | Source | Use in Rovia |
|---|---|---|
| USB Audio Class 2.0 | [USB-IF document library](https://www.usb.org/documents?search=Audio%202.0) | UAC2 descriptors, clock entities, PCM formats, endpoints and feedback |
| USB specifications | [USB-IF specifications](https://www.usb.org/documents) | USB bus and transfer rules |
| libusb API | [libusb 1.0 API reference](https://libusb.sourceforge.io/api-1.0/) | Device wrapping, interfaces, async transfers and cleanup |
| libusb async I/O | [libusb asynchronous I/O](https://libusb.sourceforge.io/api-1.0/group__libusb__asyncio.html) | Transfer submission, callbacks and cancellation |
| libusb events | [libusb event handling](https://libusb.sourceforge.io/api-1.0/group__libusb__poll.html) | Event processing and callback completion |

## UniFFI and AAudio

- [UniFFI documentation](https://mozilla.github.io/uniffi-rs/latest/)
- [UniFFI source repository](https://github.com/mozilla/uniffi-rs)
- [Android NDK AAudio guide](https://developer.android.com/ndk/guides/audio/aaudio/aaudio)

The generated Kotlin binding is regenerated from the Rust API by `scripts/build-rust.sh`; it is not hand-edited. DAP uses AAudio directly from Rust FFI, keeping the implementation Kotlin/Rust-only.

## FLAC format references

- [Xiph FLAC format overview](https://xiph.org/flac/documentation_format_overview.html)
- [Xiph FLAC bitstream format](https://xiph.org/flac/format.html)
- [Xiph FLAC API](https://xiph.org/flac/api/)

Container metadata describes the encoded source and is not proof of actual decoder output.

## Notes on using these sources

- Version pins live in `gradle/libs.versions.toml` and the Rust `Cargo.toml` files; the versioned source matching the resolved artifacts is the authority, not moving `main`/`release` pages.
- API availability does not imply device/HAL/endpoint support; the hardware pages record what the test devices actually do.
- Third-party dependency license and notice obligations (for example Symphonia's MPL-2.0) apply as they are; Rovia's own license does not change them.

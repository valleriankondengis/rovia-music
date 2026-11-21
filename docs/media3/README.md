# Media3 and ExoPlayer

Media3 and ExoPlayer are Rovia's playback and session framework in every
mode: queue, timeline, MediaSession, notification and app-facing playback
state all come from ExoPlayer. The modes differ in who decodes and where
the PCM goes — OFF decodes with Symphonia (id `symphonia-rust`) like
every mode, and
every Bit-Perfect mode decodes with Symphonia in Rust and coordinates its
native engine through a control renderer, without AudioSink interception.

The project pins Media3 `1.12.0-alpha02` (see
`gradle/libs.versions.toml`). `PlaybackService` creates one ExoPlayer and
one MediaSession; `RoviaRenderersFactory` builds the standard Android
renderers for OFF or the active mode's control renderer for a Bit-Perfect
mode.

## Documents

- [ExoPlayer pipeline by mode](EXOPLAYER_PIPELINE.md)
- [External Player](EXTERNAL_PLAYER.md) — the second, temporary MediaSessionService behind Open With, and its pause/resume handshake with the regular player.
- [Direct USB playback coordination](DIRECT_USB_PLAYBACK_COORDINATION.md)
- [Direct USB end-to-end pipeline](../direct-usb/DIRECT_USB_PIPELINE.md)
- [Decoder policy](DECODER_POLICY.md)
- [Media3 version notes](MEDIA3_UPGRADE_POLICY.md)

## Decoder policy in short

- OFF: Symphonia in Rust (id `symphonia-rust`); decoded PCM goes to a plain AudioTrack and the system mixer.
- Native A14: Symphonia in Rust; output through the Android Native
  Bit-Perfect framework/HAL route from an application-owned AudioTrack.
- Direct USB: Symphonia in Rust; output through the Rust/libusb UAC
  transport.
- DAP: Symphonia in Rust; output through an AAudio stream owned by the
  Rust session.

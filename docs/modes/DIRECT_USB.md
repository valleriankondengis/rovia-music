# Direct USB Mode

## Purpose

Decode the selected source with Symphonia in native Rust and deliver its
PCM through Rovia's Rust/libusb USB Audio Class transport directly to a
compatible DAC. Symphonia is the only Direct USB decoder, for lossless
and lossy formats alike, whenever the container/codec/profile is
supported and enabled.

## Pipeline

`MediaItem identity → Kotlin source descriptor bridge → UniFFI → Symphonia probe/demux/stream selection/decode → typed Rust PCM frame pipeline → bounded queue → asynchronous UAC transport → USB DAC`

The end-to-end description — source/USB descriptor ownership, decoder
stages, PCM mapping, queue semantics, packet scheduling, frame
accounting, flush/drain and hardware volume — is in
[DIRECT_USB_PIPELINE.md](../direct-usb/DIRECT_USB_PIPELINE.md). WAV/RIFF
uses Symphonia Wave + PCM, not Android MediaExtractor.

## Ownership

- Media3 / MediaSession: app-facing item, queue, metadata, system
  controls and state contract.
- ExoPlayer: queue/timeline/session owner; its Android audio renderer
  does not decode or output a Direct USB item.
- Kotlin: source URI and descriptor, USB permission/connection,
  commands/events and app state mapping.
- Symphonia in Rust: container demuxing, stream selection and codec
  decoding (the same core also serves DAP and Native A14).
- Rust transport: PCM frame processing, queue, descriptor-driven UAC
  configuration, clock/feedback, transfers and shutdown.
- UniFFI: the typed Kotlin/Rust boundary.

## Behavior

- No Android Codec2 decoder runs for the same item, and there is no
  custom Media3 AudioSink or ForwardingAudioSink in this path.
- PCM never crosses UniFFI; the boundary carries commands and state.
- Decoded samples, channels, sample rate, frame order and time base are
  preserved; buffering is bounded and the producer and transport are
  separate threads.
- Physical position derives from successfully completed source frames.
- Source and USB descriptors are independently owned and closed.
- Unsupported codec/profile, route, clock, source or transfer errors
  fail visibly; there is no silent fallback to Codec2 or phone-speaker
  output.
- For lossy sources the delivery is direct decoded PCM; it is not
  claimed to be bit-identical with pre-encode PCM.
- Volume is the DAC's hardware volume (see
  [HARDWARE_VOLUME.md](../direct-usb/HARDWARE_VOLUME.md)); the Android
  media volume has no effect in this mode.
- Selecting Direct USB with no DAC attached is refused and the mode
  stays OFF; unplugging the DAC reverts the mode to OFF. Entering the
  mode connects the USB session before the player swaps, and a track
  that was playing in OFF stops rather than continuing into the new
  engine (see [MODE_ORCHESTRATION.md](MODE_ORCHESTRATION.md)).

## On the test hardware

On the POCO F4 + Cayin RU3 combination, Direct USB plays local files end
to end: FLAC at 24-bit/44.1 kHz, 24-bit/48 kHz and 24-bit/96 kHz, with
seek, pause/resume and track transitions, and the DAC's hardware volume
under app control.

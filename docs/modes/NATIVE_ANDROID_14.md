# Native Android 14 Bit-Perfect Mode

## Purpose

Use Android's native bit-perfect mixer behavior through the platform
audio framework and a supporting Audio HAL, with decoding done by
Symphonia in Rust — the only decoder in every mode since the
Symphonia-only change of 2026-11-21. Because the
source's true format is known exactly from Symphonia, the mixer attribute
selected for a track must match that format exactly.

## Decoder

Symphonia in Rust decodes every format this mode plays, lossless and
lossy alike. No platform decoder is used in any mode. A format
Symphonia cannot decode fails explicitly here, and it fails in OFF too
— OFF decodes with the same Symphonia backend, only through the system
mixer.

## How it works

- **Support check.** `NativeA14Support.check` requires Android 14+, a
  connected USB output device, and at least one `MIXER_BEHAVIOR_BIT_PERFECT`
  entry in `AudioManager.getSupportedMixerAttributes` for that device —
  the app-visible sign that the vendor's AIDL HAL exposes the
  AUDIO_OUTPUT_FLAG_BIT_PERFECT mix port (see the AOSP reference in
  [REFERENCE.md](../REFERENCE.md)). Selecting the mode in Settings runs
  the check; when it fails, the selection reverts to OFF and the screen
  states the reason (Android version, no USB device, or no BIT_PERFECT
  support).
- **Playback path.** `NativeA14Renderer` is a control/clock renderer
  like the Direct USB one: it produces no PCM and uses no AudioSink. Its
  coordinator opens the source through the Rust `NativePcmSource`
  (Symphonia decode + the exact PCM adapter at the track width: 16, 24
  or 32 bits), selects the BIT_PERFECT mixer attribute that exactly
  matches the source's sample rate, channel mask and encoding via
  `NativeA14MixerController`, and builds the mode's own `AudioTrack`
  with those attributes and the USB device as its preferred device. A
  pump thread writes PCM blocks from the source into the track; position
  comes from the track's playback head position. The track volume is
  pinned to 1.0f: in this route the DAC owns volume.
- **Failure behavior.** No matching BIT_PERFECT attribute, a route
  change, or a mixer-attribute change fails the current item explicitly.
  There is no silent continuation through the standard mixer in this
  mode.
- **Cleanup.** Leaving the mode stops the pump, releases the track and
  the native source, and clears the preferred mixer attributes.
  Unplugging the USB DAC reverts the mode to OFF (see
  [MODE_ORCHESTRATION.md](MODE_ORCHESTRATION.md)).

## Separation from other modes

Native A14 is independent of Direct USB: no Rust USB transport and no
USB DAC descriptor parsing are involved. It shares the Symphonia decoder
core and the exact PCM adapter with the other Bit-Perfect modes; only
its output route is Android-side. The standard-path precision limitation
(24-bit sources reaching the output as 16-bit PCM on the test device) is
a property of the OFF path and does not apply here.

On the POCO F4 test device the HAL does not expose BIT_PERFECT, so
selecting Native A14 there reverts to OFF with the reason shown — the
designed behavior for an unsupported device.

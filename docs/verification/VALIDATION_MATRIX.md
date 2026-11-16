# Output Modes: Behavior and State

| | OFF | Native A14 | Direct USB | DAP Mode |
|---|---|---|---|---|
| Decoder | Android Codec2/platform | Symphonia in Rust | Symphonia in Rust | Symphonia in Rust |
| FLAC | Device-selected FLAC component (`c2.android.flac.decoder` when selected) | Symphonia FLAC | Symphonia FLAC | Symphonia FLAC |
| ALAC / WAV / AIFF / MP3 / AAC / Vorbis | Corresponding platform component | Symphonia, when the format is enabled | Symphonia, when the format is enabled | Symphonia, when the format is enabled |
| Output backend | Standard Android audio | Framework/HAL BIT_PERFECT route from an application AudioTrack | Rust/libusb UAC | AAudio stream owned by the Rust session |
| Position source | ExoPlayer | AudioTrack playback head | Completed Rust output frames | AAudio frames-read |
| Volume | Android media volume | The DAC (track volume pinned to 1.0f) | The DAC's hardware volume, app-controlled | The DAC / system route |

## State on the test hardware (POCO F4 + Cayin RU3)

- **OFF:** the standard path, measured as the baseline — 24-bit sources
  are truncated to 16-bit PCM before the output on this device.
- **Direct USB:** local-file playback works end to end (FLAC 24-bit at
  44.1/48/96 kHz, seek, pause/resume, track transitions, hardware
  volume). The transport underneath it — descriptor parsing, clock
  set/read-back, payload transfers, detach handling — is described in
  [the validated combination](../hardware/VALIDATED_COMBINATION.md).
- **DAP:** implemented on the shared engine; AAudio streams open on this
  device at the source's exact format. Its playback qualification is
  independent of Direct USB's.
- **Native A14:** implemented; on this phone the HAL does not expose
  BIT_PERFECT, so the mode refuses and reverts to OFF, as designed. It
  needs a device whose HAL exposes the flag to run.

No mode inherits another mode's state: a Direct USB result says nothing
about DAP or Native A14, and an Android-path measurement says nothing
about Symphonia's output.

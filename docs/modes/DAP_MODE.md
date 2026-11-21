# DAP Mode

## Purpose

DAP Mode is a low-level audio output backend using AAudio, fed by
Symphonia decoding in Rust. The native integration is Kotlin/Rust-only:
AAudio is accessed directly through Rust FFI. Oboe is not used, and no
Rovia-owned C/C++ source exists.

## Decoder

Symphonia in Rust decodes every format this mode plays, lossless and
lossy alike, through the same playback engine as Direct USB — decoder,
exact PCM adapter and bounded producer — with an AAudio-backed sink, so
format handling, seek and EOS semantics are the same across both modes
by construction. No platform decoder is used in any mode. A format
Symphonia cannot decode fails explicitly here, and it fails in OFF too
— OFF decodes with the same Symphonia backend, only through the system
mixer.

## How it works

- **Native engine.** The UniFFI object `DapSession` owns the playback
  engine: Symphonia demux/decode from the transferred file descriptor,
  the exact PCM adapter and the bounded producer, writing into an AAudio
  sink (`rust/direct-usb/src/dap.rs` and `dap_session.rs`). The stream is
  requested in exclusive sharing mode with low-latency performance mode
  at the source's exact sample rate and channel count; the adapter fills
  the narrowest AAudio sample width that holds the source's bits (16,
  24 packed or 32). If exclusive mode is unavailable, the engine retries
  once in shared mode. If AAudio negotiates a different rate, channel
  count or format than requested, the open fails explicitly instead of
  altering samples. Position comes from the AAudio frames-read counter,
  kept monotonic across stream flushes.
- **Device selection.** The stream targets the current USB audio output
  device when one is connected, otherwise the system default output
  (device id 0). The device id is resolved again at every track prepare
  and the session is recreated when it changed, because the system
  assigns a new id when a USB audio device re-enumerates — for example
  after a Direct USB session releases it.
- **Media3 bridge.** `DapRenderer` is a control/clock renderer like the
  Direct USB one: it produces no PCM and uses no AudioSink, and its
  coordinator issues the same source/prepare/start/pause/seek/stop
  commands to `DapSession` over UniFFI. No PCM crosses UniFFI in this
  mode either.
- **Failure behavior.** A stream that cannot be opened, started or kept
  alive is a route failure of the device, not of the track: the mode
  reverts to OFF and the queue continues through the standard path
  instead of skipping items. Decoder failures for formats the engine
  cannot decode stay explicit per-item failures. Selecting DAP in
  Settings first probes the output with a short AAudio open; when
  AAudio is unavailable the selection reverts to OFF with the reason
  shown.

## Limitations

- Playback speed is fixed at 1.0x in the engine (parameters are accepted
  and reported, not applied).
- There is no software volume in this mode; the DAC or the system route
  owns the level.

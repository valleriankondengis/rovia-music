# Decoder Module

> Created 2026-11-21 with the Symphonia-only decoder change (locked by
> Vall): one decoder backend for every mode, super modular so a future
> decoder can be added or Symphonia can be replaced without migrating
> the project. Rovia is going multi-platform; the decoder module is
> the platform-agnostic part. ExoPlayer / MediaSession remain
> Android-only queue/timeline drivers for now and are NOT decoders.

## 1. The rule

- **One decoder backend, all modes.** OFF (standard), Native A14,
  Direct USB and DAP all decode with the registered backend. Only the
  output route changes between modes.
- **No platform decoder, ever.** No Codec2 / `c2.android.*` /
  MediaCodec decoder in any playback path. No silent fallback to one.
- **Unsupported is explicit.** A format no backend supports stops
  playback on that item with a named reason; it is never removed from
  the queue and skipped.
- **HE-AAC sacrificed.** HE-AAC / HE-AACv2 have no SBR decoder in
  Symphonia and are unsupported in every mode (2026-11-21). A future
  backend can add them; see §4.

## 2. Backend id naming — `<decoder>-<language/tech>`

Backend ids are **canonical identifiers**, not display labels, and
always carry the technology suffix (locked 2026-11-21):

| Backend | Canonical id | UI label (spelled-out form) |
|---|---|---|
| Symphonia (current) | `symphonia-rust` | Symphonia (Rust) |
| FFmpeg (future example) | `ffmpeg-c` | FFmpeg (C) |

Never register a bare decoder name such as `symphonia` — the suffix
is what keeps multi-decoder ids unambiguous and readable. The UI
label "Symphonia (Rust)" is allowed: it is the spelled-out form of
the same id, not a second identifier. The Rust crate stays
`rovia-decoder` and the Symphonia implementation stays
`DecoderEngine`; only the exposed backend id follows the rule.

## 3. Module map

### Kotlin — `app/src/main/kotlin/com/rovia/music/playback/decoder/`

| File | Contents |
|---|---|
| `DecoderBackend.kt` | `DecoderBackend` interface (`openSource`, `readBlock`, `seek`, `releaseSource`), `DecodedSourceInfo` (backend id, container, codec, rate, channels, bits, duration, lossless, bit-perfect-eligible), `DecoderSupport` (Supported / Unsupported with reason), `DecoderFailure` |
| `SymphoniaDecoderBackend.kt` | The Symphonia backend, id `symphonia-rust`, display name "Symphonia 0.6.1 (Rust)". Wraps UniFFI `NativePcmSource`. Supported containers/codecs and the explicit unsupported table live here and nowhere else. |
| `DecoderRegistry.kt` | **The single registration point.** `registerBackend()`, `defaultBackend()` (returns a fresh instance per caller — each coordinator owns its decode state), `resolve()`, `supportedCodecs()`, `unsupportedReason()`, `probe()`. Playback code never names Symphonia directly except the registry default and the backend file. |

`playback/PlaybackDecoderStore.kt` holds the decoded source
(`decoderInfo`) and the last failure (`lastFailure`) for the pipeline
UI; it is fed only by coordinators, never by a platform hook.
The player format line (`PlayerScreen`) and Track Info sheet read
the same `decoderInfo` first (falling back to the track's library
fields), because the library no longer scans sample rate / bit depth
from a platform retriever — that is why those fields are blank in
`LocalAudioTrack` until a decoder has opened the track.

### Rust — `rust/decoder/src/`

| File | Contents |
|---|---|
| `backend.rs` | `DecoderBackend` trait (`open_path`, `decode_next`, `seek`, `info`), `SymphoniaBackend` wrapping `DecoderEngine`, `BackendRegistry` (`default_id()`, `backend_ids()`, `resolve(codec)`, `create(id)`), plus registry unit tests (supported codecs resolve to `symphonia-rust`; HE-AAC/Opus/DSD are rejected naming the codec) |
| `engine.rs` | `DecoderEngine`, the Symphonia implementation. Unsupported errors name the codec. |
| `lib.rs` | Re-exports `backend` + `engine` + `error` + `format` + `info` |

### Coordinators — one per output route, all decode the same way

| Coordinator | Output route | Decode |
|---|---|---|
| `playback/standard/StandardPlaybackCoordinator.kt` (+ `StandardRenderer`, `StandardMediaClock`) | Plain AudioTrack, media usage; mixed by the Android system. Output rate capped at 48 kHz: a track above 48 kHz is downsampled to 48 kHz (`PcmDownsampler`, linear interpolation) so the hifi output never opens and the USB backend never gets pinned at 192 kHz; 44.1/48 kHz tracks keep their native rate. | Registry default backend |
| `playback/bitperfect/a14/NativeA14PlaybackCoordinator.kt` | Native A14 BIT_PERFECT mixer route | `NativePcmSource` (Symphonia) |
| `playback/bitperfect/direct/DirectUsbPlaybackCoordinator.kt` | Rust/libusb UAC transport | `NativePcmSource` (Symphonia) |
| `playback/bitperfect/dap/DapPlaybackCoordinator.kt` | AAudio exclusive stream | Rust session (Symphonia) |
| `playback/external/ExternalPlaybackService.kt` | Same standard path as OFF | Registry default backend |

`PlaybackService.buildPlayer()` picks the renderer by mode only;
`RoviaRenderersFactory` only adds the renderer it is given and never
builds a platform audio renderer.

## 4. How to add a new backend

**Kotlin only (a backend that already exists in Rust via UniFFI):**

1. Implement `DecoderBackend` (see `SymphoniaDecoderBackend`).
2. Call `DecoderRegistry.registerBackend(BackendFactory(...))` once
   at startup. Pass `makeDefault = true` to make it the default.
3. Done. No coordinator, service or UI change.

**Rust (a new native decoder, e.g. `ffmpeg-c`):**

1. Implement the `DecoderBackend` trait in `rust/decoder/src/`.
2. Register its id in `BackendRegistry` (`backend_ids()`,
   `resolve()`, `create()`).
3. Expose it through UniFFI if Kotlin coordinators must use it, and
   add the matching Kotlin backend + registry entry as above.

**Rules:** a backend must fail explicitly (with the codec name) for
formats it cannot decode; it must never silently fall back to
another decoder or to the platform; its id must follow
`<decoder>-<language/tech>`.

## 5. How to replace Symphonia entirely

1. Register the replacement as the default in both registries.
2. Delete `SymphoniaDecoderBackend.kt` and `SymphoniaBackend` /
   `DecoderEngine` if nothing else uses them.
3. No coordinator, service, UI or docs-structure change is needed.
   The pipeline UI reads the backend id/display name from
   `DecodedSourceInfo`, so it follows the new default automatically.

## 6. Multi-platform note

The decoder module (Kotlin interface + registry, Rust trait +
registry + engine) is platform-agnostic: it takes a file descriptor
and returns decoded PCM blocks. What is still Android-only is the
shell around it: ExoPlayer/MediaSession (queue/timeline), AudioTrack
/ AAudio / libusb (output routes), and `MediaStore` (library).
Porting to desktop means replacing that shell and keeping the
decoder module; the `symphonia-rust` backend itself is already pure
Rust and multi-platform.

## 7. What is NOT supported, and why

See [../media3/DECODER_POLICY.md](../media3/DECODER_POLICY.md) §4
for the full table. The headline case is HE-AAC/HE-AACv2: Symphonia
has no SBR decoder, so it can at best decode the AAC-LC core at half
the sample rate — Rovia does not pretend that is full playback, and
sacrificed HE-AAC on 2026-11-21 rather than keep a platform decoder
just for recordings. A future `ffmpeg-c` backend is the intended way
back in, via the registry.

## 8. Pump invariant — a partial write never seeks (2026-11-21)

The Kotlin coordinators that pump decoded PCM into an `AudioTrack`
(`StandardPlaybackCoordinator`, `NativeA14PlaybackCoordinator`) keep
the unwritten remainder of a block in `pendingData`/`pendingOffset`
and finish writing it on the next pump run. The decoder only advances
when the previous block is fully written.

The old code did the opposite: on a partial write it "re-anchored" by
seeking the decoder to `positionBaseFrames + playbackHeadPosition`.
`playbackHeadPosition` is absolute since the track started, and the
re-anchor did not flush or reset the head counters, so the head was
counted twice on every partial write and the decoder raced ahead —
the song played in fast-forward jumps. Worse, a 64 KiB block is
larger than the ~53 KiB `AudioTrack` buffer (logcat: `frameCount
8820`), so a partial write happened on *every* block, not rarely.
The fix: `READ_BLOCK_BYTES` is 32 KiB (fits the buffer), and a
partial write keeps its remainder instead of seeking. `positionBaseFrames
+ head` is only valid where the track is flushed and the head counters
reset (pause / seek), never mid-pump.

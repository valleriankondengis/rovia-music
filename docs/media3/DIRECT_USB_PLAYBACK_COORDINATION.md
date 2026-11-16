# Direct USB Playback Coordination with Media3

How Direct USB's Rust playback engine is exposed through Rovia's Media3
playback/session model without passing PCM through an AudioSink. The same
pattern, with per-mode coordinators, is used by DAP and Native A14.

## 1. Separation of responsibilities

- **Media3 / app layer:** media item identity, queue semantics, metadata,
  playback commands, MediaSession, notification, application state and
  the user-facing timeline.
- **Kotlin bridge:** source URI resolution, content permission, seekable
  file descriptor acquisition, native session lifetime, command/event
  translation and UI-facing state (`DirectUsbPlaybackCoordinator`).
- **Rust engine:** Symphonia source probe/demux/decode, buffering, PCM
  frame production, USB output clock, EOS and native error state.
- **Rust transport:** libusb/UAC transfer lifecycle and hardware progress.

There is one active audio-producing engine and one app-facing
queue/session. The standard Android decoder/renderer route never
processes a Direct USB item.

## 2. The renderer: a control and clock adapter

`DirectUsbRenderer` is registered through the Rovia `RenderersFactory` as
the player's only audio renderer in Direct USB mode. It is not a PCM
renderer or sink:

- it announces support for every audio format, because format support is
  decided by the Rust engine at source open — an unsupported format fails
  explicitly there instead of silently selecting a platform decoder;
- its `getMediaClock()` returns the coordinator's clock, so ExoPlayer's
  single playback clock is the native completed-frame clock;
- readiness and end-of-stream come from the coordinator's native state,
  polled every 200 ms.

### The shadow stream and pacing

The renderer still receives ExoPlayer's sample stream for the item, but
the stream carries no audio anywhere — it is a shadow used for timeline
bookkeeping. It is drained at the engine's pace: the renderer peeks at
buffers and consumes a sample only once the engine's render position has
reached that sample's timestamp, and it consumes the end-of-stream marker
only after the engine reports output end. This matters because ExoPlayer
advances its reading period to the next item once the current stream is
fully read and the position is within 10 seconds of the period end; an
unpaced drain would end every track about 10 seconds early. Paced, the
reading advance, the stream change and the native source switch all
happen at the true end of the item.

## 3. Source descriptor ownership

1. Kotlin resolves the current `MediaItem` to an Android URI and opens a
   seekable descriptor (`contentResolver.openFileDescriptor`, detached).
2. Rust duplicates the descriptor for its reader and closes the
   transferred descriptor on every path; Kotlin's original is closed by
   the content resolver call itself.
3. The source descriptor stays valid for the whole native reader
   lifetime and is never conflated with the Android USB device
   descriptor.
4. The Rust decoder/source reader closes before the session releases the
   source.

If no Direct USB session is connected, opening a source fails explicitly
instead of queuing audio anywhere.

## 4. Clock and position

Rust reports source media position from successfully completed source
output frames with the active sample rate/time base. Queue-accepted or
submitted frames do not prove they reached the device, and silence
padding does not advance music position. The coordinator anchors its
clock on native status samples and interpolates between them with the
monotonic system clock while playing, capped at the item duration; the
renderer exposes that clock to ExoPlayer. No independent timer drifts
alongside the native output clock.

## 5. Generation boundary

Every source open, seek, track change, stop and route reconfiguration
carries a monotonically increasing generation ID. Decoder results, queue
writes and callbacks from an older generation are rejected, and a
completed flush barrier is required before the next generation's frames
may be submitted.

Track transitions get one extra guard: the renderer's stream change can
arrive before the player's current-item notification, so a prepare can
complete for the item that is still current. The coordinator re-checks
the player's current item after preparing and, if it changed meanwhile,
releases the stale source and prepares the current item instead — the
audio heard always belongs to the item the player shows.

## 6. Mode switching

Entering Direct USB is transactional about the session: the playback
service first connects the USB session — enumerating the device and
awaiting the system USB permission result — and only then swaps the
player. If no session can be established, the selection reverts to OFF
and the current player is left untouched. A prepare that finds no
retained session yet waits briefly for one (up to 2 seconds) instead of
failing the track, so a still-connecting session can never push items
into the player's error recovery. Leaving Direct USB stops the Rust
producer, flushes and drains output, closes the decoder source and
disconnects the session before the next backend is built. There are never
overlapping sound paths.

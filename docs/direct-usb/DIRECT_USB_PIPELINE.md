# Direct USB End-to-End Pipeline

## 1. Shape of the path

Direct USB is a native playback route with one source decoder, one PCM
producer/queue and one USB transport consumer:

```text
MediaItem URI
  -> Kotlin source-handle resolver
  -> owned seekable source descriptor
  -> UniFFI control/session boundary
  -> Rust source reader
  -> Symphonia probe + container demux + audio stream selection + codec decode
  -> typed decoded PCM frames
  -> exact PCM/UAC adapter
  -> bounded, frame-aligned Rust PCM queue
  -> Rust USB packetizer and asynchronous isochronous worker
  -> USB Audio Class DAC
```

The decoder producer and the USB transport consumer are separate threads
joined by the bounded queue. Symphonia, the PCM adapter, the queue and the
USB transport all live inside Rust. PCM blocks never make a round trip
through Kotlin and never pass through an ExoPlayer `AudioSink`, a
`ForwardingAudioSink` or a renderer output-buffer interception path.
ExoPlayer's Android audio decoder/renderer does not decode or output the
same Direct USB item.

## 2. Ownership by layer

| Layer | Owner | Responsibility |
|---|---|---|
| Item/session identity | Media3 / ExoPlayer | MediaItem, queue, metadata, commands, app timeline and MediaSession contract |
| Source access | Kotlin Android layer | Resolve URI, check permission, open a seekable read-only descriptor, retain the original descriptor |
| USB access | Kotlin Android layer | Enumerate device, request permission, open `UsbDeviceConnection`, retain the original connection, supply raw descriptors |
| Native boundary | UniFFI | Typed open/prepare/play/pause/seek/stop/state/error operations; no per-block PCM |
| Source/decoder | Rust | Own the duplicated source handle, seekable reader, Symphonia probe/demux/select/decode |
| PCM producer | Rust | Validate decoder frames, convert only under the PCM contract, produce frame-aligned bounded chunks, apply backpressure |
| PCM queue | Rust | Bounded FIFO between producer and transport consumer; no decode, resampling or format negotiation |
| USB transport | Rust | libusb ownership, UAC descriptor configuration, clock, feedback, packetization, transfer callbacks, flush and shutdown |
| UI and system controls | Kotlin + Media3 | Present state and route controls; translate Media3 actions to typed native commands |

The source-media descriptor and the USB-device descriptor are different
resources with different owners and lifetimes. The USB descriptor is never
used as a media source, and the two never share ownership or close state.

## 3. Preparation transaction

A Direct USB session is prepared transactionally and does not report ready
until source, decoder, PCM contract and USB output are all compatible:

1. Resolve the selected MediaItem to its original Android URI. No
   transcoded or cached substitute is used.
2. Open a read-only seekable source descriptor. A source that cannot meet
   the reader contract fails explicitly; it is not spooled or transcoded.
3. Request USB permission for the device; the permission callback is
   bound to the current session generation.
4. Open and retain `UsbDeviceConnection`; obtain its device FD and raw
   descriptors.
5. Pass the source descriptor and the USB descriptor as separate inputs
   to the native session. Rust duplicates the source descriptor for its
   reader and wraps the authorized USB descriptor through libusb.
6. Probe the source with Symphonia: identify the container, select an
   audio stream, read the codec/profile from the stream parameters and
   create the decoder. The decoder is never chosen from the file
   extension.
7. Read the decoded audio format: sample rate, channel layout/order,
   decoded sample representation, valid precision and container width,
   interleaving/planarity, timestamps and duration.
8. Resolve a compatible UAC Type I PCM alternate from the USB
   descriptors: channel count, sample rate, meaningful sample precision,
   subslot width, endpoint capacity and clock topology.
9. Configure the UAC clock from the descriptors and read it back; the
   requested rate must match before output starts.
10. Configure the PCM adapter and queue for the selected output format.
    The producer starts and fills the startup watermark.
11. Start the USB transport only when the same generation has a valid
    decoder, a prebuffered queue and a configured endpoint.

A failure at any step tears down everything already acquired, in reverse
ownership order. There is no silent fallback to a platform decoder, the phone
speaker or another output route while Direct USB is selected.

## 4. Source decoding

Symphonia owns probing, demuxing, stream selection, packet delivery and
decoding. The source reader provides the read/seek behavior the demuxer
requires and is safe to cancel and close from the playback lifecycle.

Container and codec are separate facts:

- WAV is a container; the stream may be integer PCM or floating PCM, and
  it is decoded by Symphonia's Wave demuxer and PCM decoder — Android
  `MediaExtractor` is not involved.
- M4A/MP4 can carry ALAC or AAC-LC, among other stream types; the
  extension does not choose the decoder.
- A demuxer recognizing a track does not prove a decoder for that
  codec/profile is enabled.

The decoder core is independent of Android UI, Media3 classes, libusb,
UAC descriptors and transport callbacks. Its output is a backend-independent
typed decoded-frame stream, shared with the DAP and Native A14 backends.
Unsupported codec profiles fail with a structured unsupported-source
error. The enabled Symphonia features and the unsupported formats are
listed in [the architecture document](../ARCHITECTURE.md#symphonia-build-used-by-rovia).

## 5. PCM negotiation and the adapter

The decoded PCM format and the USB format are negotiated separately. The
source side preserves:

- the actual sample rate;
- channel count, channel identity and channel order;
- the decoder sample type (signed integer width or floating-point width);
- meaningful valid bits and the memory container width;
- signedness, byte order and alignment of the meaningful bits;
- planar/interleaved layout;
- frame count and source timestamp/time base.

The USB side separately describes UAC bit resolution, subslot bytes,
channels, alternate setting and endpoint capacity. A 24-bit sample carried
in a 4-byte subslot is still 24 meaningful bits; the subslot is not
reported as source precision.

The adapter performs only transformations whose sample behavior is exact
and covered by tests (see
[the exact PCM adapter](../pcm/EXACT_PCM_ADAPTER.md)). For lossless
integer PCM: exact repacking, sign extension/padding and little-endian
serialization, preserving every meaningful sample bit and the channel
order. Integer audio is never routed through Float32. If the decoder
produces floating samples for a lossless source, an integer USB output
counts as sample-exact only when every converted value is exactly
representable under the defined mapping.

Lossy codecs commonly decode to floating-point PCM. A deterministic
finite-range conversion to the selected USB integer format is provided as
direct decoded-PCM output; it is not bit-identical to the PCM that existed
before lossy encoding. No gain, EQ, ReplayGain, normalization, resampling,
channel mixing, crossfade or speed/pitch processing is applied in either
case, and values are never silently dithered or clipped.

## 6. Producer, queue and consumer

The decoder producer and the USB transport worker never share mutable
decoder state and never call each other synchronously from USB callbacks.
They exchange PCM only through the bounded queue and state/notification
primitives.

### Producer

1. Decode a bounded block from Symphonia.
2. Validate its format metadata against the prepared output format.
3. Convert only through the PCM adapter.
4. Form a chunk of complete interleaved PCM frames, at most 256 KiB.
5. Query available capacity as an optimization, not a correctness
   guarantee.
6. Submit the chunk. The accepted byte count is authoritative and may be
   smaller than requested.
7. Retain the unaccepted suffix and retry after queue capacity changes.
   Accepted and unaccepted bytes are never dropped, re-copied in full or
   busy-spun on.
8. Stop producing immediately for pause, seek, stop, source failure, USB
   route failure or a stale generation.

### Startup prebuffer

The USB transport does not start on an empty queue merely because the
decoder opened. The producer first fills a startup watermark of
min(capacity / 2, sample rate / 5) frames; the transport starts when the
watermark is reached, or at end of source when everything decoded is
queued.

### Queue

The `PcmQueue` is a bounded byte FIFO configured to the complete
interleaved frame size:

- maximum write request: 256 KiB;
- target capacity: approximately 200 ms of PCM;
- capacity clamp: 16 KiB minimum and 1 MiB maximum;
- frame-aligned writes and consumption;
- partial writes when capacity is insufficient;
- the accepted byte count is authoritative;
- `available_bytes` is advisory.

The queue is a transport buffer, not a decoder, adapter, timestamp store
or playback-position source. Frame metadata and generation/timestamp
accounting live in the producer/session state around it.

### Consumer

The USB worker schedules packets from the device's service interval and
the current feedback/clock state, copying complete queued source frames
into stable asynchronous OUT transfer buffers. When fewer source frames
are queued than the packet schedule requires, the packet tail is
zero-filled; those silence frames are counted independently and never
advance source-media position. The startup prebuffer keeps this from
happening at the beginning of a track.

## 7. USB packet transport and clock

The transport owns libusb, interface claims, alternate selection, transfer
buffers, callbacks, the packet clock, feedback and shutdown:

- descriptor-derived AudioControl and AudioStreaming topology;
- the selected UAC Type I PCM alternate and validated endpoint size;
- UAC2 clock-source `GET_CUR`, conditional `SET_CUR`, and exact read-back;
- service-interval scheduling derived from bus speed and the OUT
  endpoint's `bInterval`;
- separate feedback polling and OUT scheduling;
- Full-Speed 10.14 and High-Speed 16.16 feedback interpretation;
- fractional frame accumulation, so the long-term rate does not drift;
- stable async transfer storage until callbacks complete;
- four OUT transfer slots and a separate feedback slot;
- cancellation and callback draining before buffer release.

No endpoint, alternate, interface or clock entity is hardcoded from a
product name. UAC Raw Data is not treated as PCM.

## 8. Playback clock and frame accounting

Decoded frames, queue-accepted frames, source frames submitted, source
frames in flight, successfully completed source frames, flushed/discarded
source frames and silence frames are counted separately. Transfer counts,
queue bytes, feedback frames and decoder read-ahead are not playback
position.

For a fixed-rate PCM session, the native output position derives from
successfully completed source frames mapped through the source time base
and the active sample rate, plus the current item/seek base offset.
Silence padding does not advance it. A partially completed or failed
transfer credits only the source frames the completion logic can prove
were delivered.

The Direct USB native clock is the only authoritative audio-output clock
for this route. Kotlin/Media3 exposes it through the mode's renderer and
media clock; no ExoPlayer AudioTrack clock or independent timer competes
with it.

## 9. Pause, seek, flush, EOS and track change

A monotonically increasing playback generation covers open, seek, item
transition and route reconfiguration.

- **Pause:** decoder production stops; the output is flushed; the
  reported position is the completed-frame position, not the decode-ahead
  position.
- **Seek:** the producer stops, queue admission closes for the old
  generation, a flush barrier cancels OUT/feedback transfers and drains
  their callbacks, the queue is cleared, the decoder seeks (with codec
  preroll handled by timestamp), packet-clock fractional state resets,
  and only then are new-generation frames admitted.
- **Resume:** the startup watermark is refilled before the transport
  restarts; completed frames are never replayed and old-generation frames
  never leak.
- **Track change:** decoder/source state is recreated under a generation
  boundary. If the rate or alternate changes, output stops, the UAC side
  is reconfigured and the clock read-back is verified before restart.
- **EOS:** decoder EOS is not output EOS. Decoded frames, the PCM queue
  and all in-flight USB transfers drain before completion is reported.
- **Failure/detach:** the producer stops, transfers are cancelled and
  drained, workers join, source and USB resources close, and a route
  failure is reported. Sound is never routed to the phone speaker
  silently.

## 10. Hardware volume

Volume belongs to the USB Audio Class Feature Unit on the playback chain,
in signed 1/256 dB units, set with class control transfers and verified by
read-back — never as PCM gain. On connection the DAC is attenuated to at
most −40 dB (never raised), and a DAC whose volume cannot be controlled
requires the user's "allow fixed USB output volume" setting before
playback starts. The full mechanism is described in
[HARDWARE_VOLUME.md](HARDWARE_VOLUME.md).

## 11. Media3 coordination

The app keeps one MediaItem queue and one MediaSession. `DirectUsbRenderer`
is a lifecycle/clock adapter, not a PCM renderer or sink: it selects the
native session for Direct USB items, keeps any platform audio renderer
out of those items (there is none left in Rovia — `RoviaRenderersFactory`
never builds one), forwards player commands to
the Rust session and exposes the native completed-output clock to Media3.
How the renderer paces its shadow sample stream, and how mode switches are
transactional, is described in
[the coordination document](../media3/DIRECT_USB_PLAYBACK_COORDINATION.md)
and [mode orchestration](../modes/MODE_ORCHESTRATION.md).

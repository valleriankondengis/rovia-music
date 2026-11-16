# Direct USB Lifecycle and Playback Position

The session and worker contract is in
[DIRECT_USB_PIPELINE.md](DIRECT_USB_PIPELINE.md). This page summarizes the
lifecycle rules and the position model.

## 1. Preparation and start

The source descriptor, Symphonia stream, typed PCM contract, UAC
alternate and verified clock are all ready before the session reports
ready. The decoder producer fills the startup watermark before the USB
transport starts. The source-media FD stays independent from the
USB-device FD at every step.

## 2. Position model

Decoded, accepted, submitted, in-flight, completed and discarded source
frames are tracked separately. Physical position derives only from
successfully completed source frames, mapped through the active sample
rate/time base and the item/seek base offset. Queue acceptance and USB
submission are not output completion. Silence frames inserted by
packetization do not advance source position.

## 3. Pause and resume

Pause stops decoder production and flushes the output; the reported pause
position is based on completed frames, not decoder read-ahead. Resume
re-seeks the decoder to the completed-frame position and refills the
startup watermark before restarting, so no completed frame is replayed
and no pending source frame is skipped.

## 4. Seek and discontinuity

- The generation increments.
- Decoder packet reads and new producer writes stop.
- Queue admission closes for the old generation.
- Pending OUT/feedback transfers are cancelled and their callbacks drain.
- Queued old frames are cleared and in-flight counters reconciled.
- The source/demuxer seeks and the decoder resets; preroll is trimmed by
  block timestamp.
- Packet-clock fractional state resets where required.
- The watermark refills and output starts only when the new generation is
  ready.
- Stale-generation decoded blocks and callbacks are rejected.

## 5. Track transition and EOS

No frame of the old MediaItem enters the new item: source and decoder
state are recreated under a generation boundary. If the output format
changes, output stops, the UAC alternate/clock is reconfigured, the
read-back is verified, and output restarts. Decoder EOS is not output
EOS — decoded frames, queued source PCM and all in-flight transfers drain
before completion is reported.

## 6. Disconnect and errors

On detach or failure the producer stops, transfers are cancelled and
drained, workers join, and the source reader, native USB session and
Android descriptors close in ownership order. Direct USB audio is never
silently rerouted to the phone speaker; the mode itself reverts to OFF
when the DAC disappears (see
[mode orchestration](../modes/MODE_ORCHESTRATION.md)).

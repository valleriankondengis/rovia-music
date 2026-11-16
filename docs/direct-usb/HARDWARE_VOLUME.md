# Direct USB Hardware Volume

## 1. What it is

In Direct USB mode the Android media volume does nothing: the PCM leaves
the phone untouched, so level is owned by the DAC. Rovia controls that
level through the DAC's own USB Audio Class Feature Unit — the hardware
volume control on the playback chain — in signed 1/256 dB units, using
class control transfers on the AudioControl interface. Volume is never
implemented as PCM gain, an AudioSink gain stage or Android software
volume in this route.

The implementation is `rust/direct-usb/src/volume.rs`, wired into the
native session and exposed over UniFFI as `dacVolume()` /
`setDacVolume(db)`.

## 2. Discovery

When the session first configures PCM output, the volume control is
resolved from the device's raw descriptors:

1. Parse the active configuration's AudioControl interface: Input
   Terminals, Output Terminals and Feature Units.
2. Walk the playback chain from each Output Terminal upstream, through
   source links, to the Input Terminal that the selected streaming
   interface feeds.
3. The first Feature Unit on that chain is the candidate. If different
   output terminals lead to different Feature Units, the topology is
   ambiguous and volume is unavailable — capture chains are never
   touched.
4. Writability comes from the unit's control bitmaps: for UAC2, a
   channel's volume control counts as read/write when its bitmap marks
   it so; for UAC1, when the volume bit is set. The master channel (0) is
   controlled when writable; otherwise every channel must be writable
   and is controlled individually.

## 3. Open and the startup attenuation

Opening reads, for each controlled channel, the current value and the
supported range (UAC2 `GET_RANGE`; UAC1 `GET_MIN`/`GET_MAX`/`GET_RES`).
The overall range is the intersection across channels, clamped to at most
0 dB, and the device must be able to reach −40 dB.

Each channel is then set to the quieter of its current value and
−40 dB: connecting a DAC never raises its volume. A silent channel
(−∞) is left silent. Every set is verified by reading the value back;
a mismatch makes the volume unavailable rather than trusting the write.

## 4. Setting the volume

`setDacVolume(db)` accepts a finite value between −128 dB and 0 dB,
converts it to 1/256 dB units rounding down, snaps it down to the
device's range step grid per channel, writes it with `SET_CUR` (request
0x01 for both UAC versions; reads use `GET_CUR` 0x01 on UAC2 and 0x81 on
UAC1, with `wValue` = volume selector 0x02 in the high byte plus the
channel, `wIndex` = Feature Unit id plus interface) and verifies by
read-back. The reported current value is the loudest controlled channel.

## 5. Behavior when volume cannot be controlled

A DAC whose Feature Unit chain is ambiguous, not writable, unable to
attenuate to −40 dB, or failing read-back reports volume as unavailable.
Playback then waits: the Direct USB coordinator prepares the track but
does not start the engine until the user enables **Allow fixed USB output
volume** in Settings → Audio, which accepts the level set on the DAC
itself. The player output sheet shows a note instead of a slider in that
case. With a controllable DAC, the sheet's media volume section becomes
the DAC volume slider, spanning the device's own dB range.

## 6. Tests

The Rust tests cover discovery on a descriptor fixture (correct unit,
terminal link and interface; wrong links rejected), the startup
attenuation (loud device pulled to −40 dB, quiet device never raised,
silent channel preserved), read-back mismatch failing the open, and
`set` rounding down to the step grid and rejecting out-of-range values.

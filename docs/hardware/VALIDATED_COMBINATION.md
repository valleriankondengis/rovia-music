# Validated Combination: POCO F4 + Cayin RU3

What the test phone and the test DAC do together with Rovia. Published
specifications are not listed as capabilities; everything below was run
on the physical combination.

## The combination

- Xiaomi POCO F4 (`munch`, Snapdragon 870), AOSP-based custom ROM,
  Android 16.
- Cayin RU3 USB DAC in UAC2 mode (VID 0x2D87, PID 0x003B, firmware
  V1.0.3), connected over USB-C OTG.

## What runs on this combination

| Capability | State on this combination |
|---|---|
| Android USB session and Rust ownership | Works: permission, connection, descriptor hand-off to Rust, ordered close |
| UAC2 descriptor/topology parsing | Works: AudioControl/AudioStreaming resolution, clock entity chain, PCM alternates 1/2/3 plus a Raw Data alternate |
| PCM alternate and clock configuration | Works: clock set and read back exactly at 44.1 kHz and 48 kHz |
| Asynchronous transport | Works: synthetic PCM payload transfers with integrity checking, repeated audit and stress cycles, flush/cancel/drain; detaching during active transfers cleans up with a retained `LIBUSB_ERROR_NO_DEVICE` warning from libusb |
| Direct USB local-file playback | Works: FLAC 24-bit at 44.1 kHz, 48 kHz and 96 kHz played end to end to the DAC, with seek, pause/resume spam and track transitions; Android's own volume has no effect in this mode, the DAC's hardware volume does |
| DAC hardware volume | Works: Feature Unit discovery, startup attenuation to at most −40 dB, app-controlled set with read-back |
| DAP mode | Implemented on the same engine; AAudio streams open on this device at the source format. Full playback qualification of this mode is separate from Direct USB's |
| Native A14 | The POCO F4's audio HAL does not expose the BIT_PERFECT mixer attribute, so the mode correctly refuses on this phone and the selection reverts to OFF |
| OFF (standard path) | Measured baseline: 24-bit sources are truncated to 16-bit PCM before the Android output on this device (see [Verification](../verification/README.md)) |

Direct USB results do not carry over to Native A14 or DAP: each mode has
its own output route and its own state in the table above.

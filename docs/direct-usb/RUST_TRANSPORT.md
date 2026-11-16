# Rust USB Transport

## 1. Ownership

The Direct USB Rust backend owns the libusb context/handle wrapping the
Android-authorized USB descriptor, the resolved AudioControl and
AudioStreaming interfaces and clock path, the claimed interfaces, the
alternate setting, the worker/event loop, the asynchronous OUT and
feedback transfers, the stable transfer buffers, the bounded PCM queue,
the packet clock, callbacks, cancellation, flush and resource release.

Symphonia decoding is a separate Rust responsibility. The decoder producer
submits complete PCM frames into the bounded queue and the USB worker
consumes them. No Kotlin UI or Media3 renderer code calls libusb
directly, and no PCM buffer is copied through Kotlin/UniFFI.

## 2. Implementation shape

The transport lives in the `rovia-uniffi` crate (`rust/direct-usb`).
Android links `libusb1-sys` with its vendored feature (libusb 1.0.27) and
targets `arm64-v8a` / `aarch64-linux-android`.

There are four stable asynchronous OUT transfer slots and a separate
feedback slot. Each slot retains its transfer object, packet descriptors
and backing memory until its callback completes.

## 3. Worker responsibilities

A single worker thread serializes retained-session access,
control/configuration commands, packet preparation, OUT
submission/completion, feedback polling/interpretation, libusb event
processing, flush barriers, cancellation and cleanup. Decoder code never
runs inside libusb callbacks, and callbacks never invoke Symphonia state.

## 4. Transfer lifetime and packet clock

- A buffer is never moved, reused or freed while an asynchronous transfer
  references it.
- Libusb event processing continues during cancellation until every
  pending callback completes.
- Service intervals derive from bus speed and the OUT endpoint's
  `bInterval`.
- OUT data scheduling is kept separate from the feedback polling interval.
- Full-Speed feedback is interpreted as 10.14 and High-Speed feedback as
  16.16 fixed-point.
- Feedback is validated around the configured nominal rate.
- Fractional frame accumulation avoids long-term rate drift.
- Scheduled packet sizes are validated against the endpoint's maximum
  capacity.
- The transfer window is approximately 4 ms, with at most 32 packets per
  transfer slot.

## 5. Source-frame and silence accounting

The packetizer distinguishes source music frames from zero-filled silence
frames. Transfer callbacks update submitted/in-flight/completed counters
only for successful completions. Silence, feedback counts and transfer
counts do not advance source media position.

## 6. Hardware volume

The transport also carries the UAC Feature Unit hardware volume: the
session opens it at the first PCM configuration, applies the startup
attenuation and serves volume get/set commands from the worker thread.
See [HARDWARE_VOLUME.md](HARDWARE_VOLUME.md).

## 7. On the test hardware

On the POCO F4 + Cayin RU3 combination the transport was exercised with
descriptor parsing and topology resolution, clock set/read-back at
44.1 kHz and 48 kHz, synthetic PCM payload transfers with integrity
checking, repeated audit cycles, stress cycles and detach during active
transfers. Detach cleanup completes with a retained `LIBUSB_ERROR_NO_DEVICE`
warning from libusb — expected when the device disappears mid-teardown.
Real local-file playback through this transport is covered in
[the validated combination](../hardware/VALIDATED_COMBINATION.md).

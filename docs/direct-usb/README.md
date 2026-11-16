# Direct USB Native Playback Backend

Direct USB is the dedicated native Rust playback route. Symphonia decodes
every supported lossless and lossy stream; WAV/RIFF is decoded through
Symphonia Wave + PCM, not Android MediaExtractor. Rust owns source reading,
decoding, PCM adaptation, the bounded producer queue and the libusb/UAC
transport. Kotlin owns Android URI/descriptor acquisition, USB permission
and connection, and app/session coordination. Media3/ExoPlayer remains the
app-facing playback/session framework; there is no Direct USB AudioSink or
PCM-forwarding path.

## Documents

- [Direct USB end-to-end pipeline](DIRECT_USB_PIPELINE.md)
- [Symphonia native playback engine](SYMPHONIA_PLAYBACK_ENGINE.md)
- [Kotlin, descriptors and UniFFI](ANDROID_USB_UNIFFI.md)
- [PCM queue and packetization](QUEUE_AND_PACKETIZATION.md)
- [UAC descriptor and clock configuration](UAC_DESCRIPTOR_AND_CLOCK.md)
- [Rust USB transport](RUST_TRANSPORT.md)
- [Lifecycle and position](LIFECYCLE_AND_POSITION.md)
- [Hardware volume](HARDWARE_VOLUME.md)

## Ownership summary

| Area | Owner |
|---|---|
| MediaItem, queue, MediaSession | Media3 / ExoPlayer |
| URI, source descriptor, USB permission and original USB connection | Kotlin Android layer |
| Source reader, Symphonia, PCM metadata, adapter and producer | Rust audio engine |
| Bounded PCM queue and isochronous consumer | Rust Direct USB transport |
| libusb, UAC descriptors, clock, feedback and transfer lifecycle | Rust |
| Commands, state and errors | UniFFI generated Kotlin bindings |

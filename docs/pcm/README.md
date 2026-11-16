# PCM Integrity and Frame Contract

This section defines how Rovia represents decoded PCM and preserves sample values between a decoder and the selected output backend.

## Decoder ownership by mode

- OFF: Android Codec2/platform decoder.
- Native A14: Symphonia decoder in Rust; the output follows the Android Native Bit-Perfect framework/HAL route.
- Direct USB: Symphonia decoder in Rust for enabled and verified supported lossless and lossy codecs. WAV/RIFF is assigned to Symphonia Wave + PCM.
- DAP: Symphonia decoder in Rust through the shared playback engine over an AAudio sink.

There is no shared Media3 PCM handoff path for any Bit-Perfect mode. Direct USB and DAP decoded frames remain inside the Rust playback engine; Native A14 pulls whole decoded blocks from the Rust source into its own AudioTrack. No mode reintroduces the retired AudioSink-forwarding approach.

A label such as “24-bit” or “32-bit” is not enough to describe a sample. Keep source precision, decoder output type, valid bits, memory container, alignment, channel order, frame count, UAC resolution and subslot width separate.

For lossless sources, exact decoded-sample preservation can contribute to a Bit-Perfect claim. For lossy sources, the decoder output can be transmitted without further sample processing, but it is not bit-identical to the pre-encode source PCM.

## Direct USB PCM contract

The authoritative decoder-to-output, format negotiation and accounting sequence is [DIRECT_USB_PIPELINE.md](../direct-usb/DIRECT_USB_PIPELINE.md). The exact sample mapping rules are defined in [EXACT_PCM_ADAPTER.md](EXACT_PCM_ADAPTER.md).

## Documents

- [PCM format model](PCM_FORMAT_MODEL.md)
- [Exact PCM adapter](EXACT_PCM_ADAPTER.md)
- [Bit-Perfect contract](BIT_PERFECT_CONTRACT.md)

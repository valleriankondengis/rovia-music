# Output Modes

The settings selector has four radio choices: **OFF**, **Native A14**,
**Direct USB** and **DAP Mode**. OFF is the normal non-Bit-Perfect state;
the other three are Bit-Perfect modes.

| Mode | Decoder | Output |
|---|---|---|
| OFF | Android Codec2/platform decoder | Standard Media3/Android audio |
| Native A14 | Symphonia in Rust | `MIXER_BEHAVIOR_BIT_PERFECT` through framework/HAL, from an application-owned AudioTrack |
| Direct USB | Symphonia in Rust | Rust/libusb UAC USB output |
| DAP Mode | Symphonia in Rust | AAudio backend owned from Rust |

Every Bit-Perfect mode decodes with Symphonia in Rust, for lossless and
lossy codecs alike, so the decoder never changes when a queue moves
between lossless and lossy tracks. Android Codec2/platform decoders serve
OFF only; `c2.android.flac.decoder` is the FLAC component of that path
when selected by the platform, and MP3/AAC use their corresponding
platform components there. No Bit-Perfect mode falls back to Codec2 for a
format Symphonia cannot decode; the item fails explicitly in that mode.

## Documents

- [Mode orchestration](MODE_ORCHESTRATION.md)
- [Native A14](NATIVE_ANDROID_14.md)
- [Direct USB](DIRECT_USB.md)
- [DAP Mode](DAP_MODE.md)

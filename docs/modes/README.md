# Output Modes

The settings selector has four radio choices: **OFF**, **Native A14**,
**Direct USB** and **DAP Mode**. OFF is the normal non-Bit-Perfect state;
the other three are Bit-Perfect modes.

| Mode | Decoder | Output |
|---|---|---|
| OFF | Symphonia in Rust (id `symphonia-rust`) | Standard Android audio (system mixer) |
| Native A14 | Symphonia in Rust | `MIXER_BEHAVIOR_BIT_PERFECT` through framework/HAL, from an application-owned AudioTrack |
| Direct USB | Symphonia in Rust | Rust/libusb UAC USB output |
| DAP Mode | Symphonia in Rust | AAudio backend owned from Rust |

Every mode decodes with Symphonia in Rust (id `symphonia-rust`), for
lossless and lossy codecs alike, so the decoder never changes when a
queue moves between lossless and lossy tracks or when the output mode
changes; only the output route changes. The platform decoder was
removed from all modes on 2026-11-21. No mode falls back to a platform
decoder for a format Symphonia cannot decode; the item fails explicitly
in every mode.

## Documents

- [Mode orchestration](MODE_ORCHESTRATION.md)
- [Native A14](NATIVE_ANDROID_14.md)
- [Direct USB](DIRECT_USB.md)
- [DAP Mode](DAP_MODE.md)

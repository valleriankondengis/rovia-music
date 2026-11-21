# PCM Format Model

## 1. Why one bit-depth value is insufficient

A PCM value has multiple independent properties. Do not collapse them into a single `bits` field or infer them from a filename.

| Property | Meaning |
|---|---|
| Encoded source precision | Significant source information described by the container/codec |
| Decoder sample type | Actual numeric type returned by the decoder, such as signed integer or floating point |
| Actual valid precision | Meaningful information the decoded sample retains |
| Memory container width | Bits used to store one decoded sample |
| Alignment | Position of meaningful bits inside the memory container or USB subslot |
| Signedness | Signed/unsigned sample interpretation |
| Endianness | Byte order of the stored representation and required USB serialization |
| Channel layout/order | Speaker/channel identity and sequence |
| Storage layout | Planar or interleaved |
| Sample rate | Samples per channel per second |
| PCM frame | One sample for every channel at one time |
| UAC bit resolution | Meaningful bits advertised by the chosen USB alternate |
| UAC subslot width | Bytes allocated per channel sample in a USB Audio Class packet |

## 2. Decoder source by mode

- OFF: Symphonia native Rust decoder (id `symphonia-rust`); only the output route is the standard Android audio system.
- Native A14: Symphonia native Rust decoder; only the output route follows the Android Native Bit-Perfect framework/HAL path.
- Direct USB: Symphonia native Rust decoder for all explicitly enabled and verified supported lossless and lossy streams. WAV uses Symphonia Wave/RIFF plus PCM decoding, not Android MediaExtractor.
- DAP: Symphonia native Rust decoder through the shared playback engine over an AAudio sink.

## 3. Integer PCM mapping

Preserve the exact signed numerical sample value and meaningful bits. For example, packed signed 24-bit PCM and a 24-valid-bit sample in a 32-bit memory container are different layouts. A UAC 24-bit resolution sample may be carried in a 3-byte or 4-byte subslot, depending on the actual alternate descriptor. Implement and test sign extension, padding and bit alignment explicitly.

Widening cannot restore precision lost upstream. Float32 cannot exactly represent every signed 32-bit integer. Do not convert integer samples through a floating intermediate unless exactness is proven for the full allowed range.

## 4. Floating-point PCM

Retain the actual floating representation and document the conversion policy separately. Validate finite values and defined numeric range. For a lossless source, an integer USB output may be called sample-exact only when every sample maps exactly under the documented representation. If not, the representation is unsupported for a lossless Bit-Perfect claim.

For lossy sources that decode to float, a deterministic float-to-integer conversion can provide direct decoded-PCM output. It is not bit-identical to the pre-encode master PCM. Do not silently clip, dither, normalize or resample. A non-finite/out-of-range sample must follow an explicit error policy.

## 5. Planar/interleaved and channel ordering

The decoder's storage layout is independent of the DAC packet layout. Interleaving or deinterleaving is permitted only if channel identity and order remain exact. The current Rovia UAC parser/transport scope is 1–2 channels for the supported Type I PCM route; multichannel output must be rejected until descriptor selection, packet layout and tests support it.

## 6. Format provenance

Container metadata describes encoded input; decoder output describes the samples actually delivered to the PCM adapter. The useful record for a format claim is: source container/codec/profile, decoder version, source metadata, actual decoded representation, adapter output format, sample rate, channels, frame count, PTS/time base, output UAC resolution/subslot and the sample comparison result.

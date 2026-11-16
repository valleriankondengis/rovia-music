# USB DAC: Cayin RU3

## 1. Device identity and supported USB modes

The tested DAC is a Cayin RU3 portable USB DAC/headphone amplifier. The product documentation states that it supports both USB Audio Class 1.0 and USB Audio Class 2.0. The device can be switched between UAC modes; its display/manual identifies U1 as UAC1.0 and U2 as UAC2.0.

The Rovia runtime captures identify the attached unit as `Cayin RU3 (U2)`. Therefore, the captured Rovia descriptor parsing and transport validation apply to the UAC2 mode. UAC1 support is a device capability, but a separate UAC1 descriptor and Rovia transport qualification has not been supplied.

## 2. Manufacturer/distributor published capabilities

The published RU3 product information lists:

| Property | Published value | Source |
|---|---|---|
| USB audio bridge | SPV5068 | Product/manual listing |
| DAC chip | ESS ES9069Q | Product/manual listing |
| Op-amp | 2 × OPA1602 | Product/manual listing |
| Headphone amplifier | 2 × SGM8262 | Product/manual listing |
| Display | 0.96-inch IPS LCD | Product/manual listing |
| USB modes | UAC1.0 and UAC2.0 | Product/manual listing |
| UAC1.0 mode | Driver-free operation | Product/manual listing |
| UAC2.0 maximum input | Up to PCM 32-bit / 768 kHz | Product/manual listing |
| Native DSD input | Up to DSD512 | Product/manual listing |
| Output connections | 3.5 mm single-ended and 4.4 mm balanced | Product/manual listing |
| Output use | Headphone and line output (shared PO/LO connections) | Product/manual listing |
| Dimensions | 60 × 25.4 × 11 mm | Product/manual listing |
| Weight | 24 g | Product/manual listing |

The UAC2 maximum of 32-bit/768 kHz and DSD512 is the DAC's published input capability. It is not a guarantee that the current Android decoder, Media3 renderer, Rovia PCM adapter, USB endpoint or selected alternate can deliver those formats.

The published product listing contains the following output specifications:

| Measurement | 3.5 mm single-ended | 4.4 mm balanced |
|---|---:|---:|
| Output power at 32 Ω | 180 mW + 180 mW | 560 mW + 560 mW |
| THD+N | 0.0018% (32 Ω, 1 kHz, 1.0 V RMS) | 0.0015% (32 Ω, 1 kHz, 2.0 V RMS) |
| SNR (A-weighted) | 111 dB on the technical listing | 117 dB |
| Dynamic range | 111 dB on the technical listing | 117 dB |
| Frequency response | 20 Hz–50 kHz (±0.2 dB) | 20 Hz–50 kHz (±0.2 dB) |
| Crosstalk | 82 dB | 105 dB |
| Output impedance | 0.70 Ω | 1.20 Ω |

The manual reproduction lists 115 dB for the single-ended SNR/dynamic range, while the product technical page lists 111 dB. This documentation preserves the technical page value in the table and flags the source discrepancy rather than merging the values. Verify against a current manufacturer-issued datasheet before using these measurements for formal product claims.

## 3. PCM payload-rate reference

The following values are theoretical uncompressed PCM payload rates for two-channel audio, calculated as:

`payload bits per second = sample rate × channel count × bits per sample`.

| Stereo profile | Raw PCM payload rate | Approx. decimal payload rate |
|---|---:|---:|
| 44.1 kHz / 16-bit | 1,411,200 bit/s | 1.4112 Mbit/s |
| 48 kHz / 16-bit | 1,536,000 bit/s | 1.536 Mbit/s |
| 192 kHz / 24-bit | 9,216,000 bit/s | 9.216 Mbit/s |
| 384 kHz / 32-bit | 24,576,000 bit/s | 24.576 Mbit/s |
| 768 kHz / 32-bit | 49,152,000 bit/s | 49.152 Mbit/s |

These figures describe the uncompressed PCM payload only. They exclude USB protocol overhead, service-interval scheduling, feedback, packet headers and transport behavior. They are not measured Rovia throughput and do not establish that an input profile is accepted by the current decoder, UAC alternate or host route. A compressed FLAC file's encoded bitrate is variable and is not the same as its decoded PCM payload rate.

## 4. Rovia-observed USB identity

The supplied Android USB dump and Rovia logs report:

| Field | Observed value |
|---|---|
| Manufacturer string | `Cayin` |
| Product string | `Cayin RU3 (U2)` |
| Serial string | `RU3` |
| VID | `0x2D87` |
| PID | `0x003B` |
| USB descriptor version (`bcdUSB`) | `0x0200` in the captured raw device descriptor |
| Raw descriptor length | 361 bytes |
| Rovia scan device ID | 1002 in the supplied capture |
| Parsed interface/alternate records | 7 records across interface numbers 0, 1 and 2 |
| Parsed endpoint records | 10 |
| Parsed format records | 4 |
| Parsed clock entities | 1 |

The Android `UsbDevice.interfaceCount` is logged as 7 because alternate interface records are surfaced in the Android interface collection. The descriptor parser reports interface numbers 0 (HID), 1 (AudioControl) and 2 (AudioStreaming), with AudioStreaming alternate settings 0 through 4. These counts are not contradictory: interface-number count and alternate-record count are different concepts.

## 5. Observed UAC2 topology

| Item | Rovia-observed value |
|---|---|
| AudioControl | Interface 1, UAC2 |
| AudioStreaming | Interface 2, UAC2 |
| Clock Source | Entity 1, attached to AudioControl interface 1 |
| USB Streaming Input Terminal | Entity 3 |
| Headphones Output Terminal | Entity 4, source entity 10 |
| Feature Unit | Entity 10, source entity 3 |
| PCM alternate 1 | Stereo, 16-bit, 2-byte subslot |
| PCM alternate 2 | Stereo, 24-bit, 3-byte subslot |
| PCM alternate 3 | Stereo, 32-bit, 4-byte subslot |
| Alternate 4 | Raw Data; not classified as PCM and not assumed to mean DSD/DoP |
| OUT endpoint | `0x07`, asynchronous isochronous Data, max packet 776 bytes, `bInterval=1` |
| Feedback endpoint | `0x83`, isochronous IN Feedback, max packet 4 bytes, `bInterval=4` |
| Clock initial `GET_CUR` | 768000 Hz in the captured session |

The OUT endpoint's interval governs the PCM data schedule. Feedback polling is handled separately. Do not use feedback endpoint `bInterval` to compute OUT data packet timing.

## 6. Clock tests

Rovia's UAC2 clock control path uses a Clock Source `GET_CUR`, a conditional `SET_CUR` when the host-programmable rate differs, and a mandatory `GET_CUR` read-back. Physical tests have observed:

- initial current clock at 768000 Hz;
- successful configuration/read-back at 48000 Hz;
- an earlier successful configuration/read-back at 44100 Hz.

A captured configuration session shows the same path concretely: 48 kHz stereo PCM16, Clock Source entity 1, a `SET_CUR` operation and a 48000 Hz read-back, interface 2 alternate 1 with OUT endpoint `0x07`, feedback endpoint `0x83`, and a logged packet requirement of 24/776 bytes.

This does not establish that every rate is supported at every subslot or that the maximum advertised 768 kHz profile has been qualified by Rovia.

## 7. Rovia transport validation

### Non-zero PCM payload test

The recorded test used 48 kHz stereo PCM16 and sent a deterministic synthetic payload through the Rust queue and physical USB OUT path:

- payload bytes: 1,572,864;
- expected frames: 393,216;
- completed OUT transfers: 2,064;
- valid feedback packets: 8,258;
- failed transfers: 0;
- integrity errors: 0;
- accepted/completed/discarded source-frame accounting reconciled;
- queue and in-flight source-frame counts returned to zero;
- no pre-flush replay was recorded.

This was a synthetic test source, not a decoded local music file or Media3 output.

### Six-cycle physical stress test

Six consecutive 48 kHz stereo cycles used the 16-, 24- and 32-bit alternate configurations in the order 16, 24, 32, 16, 24, 32. The aggregate record reported:

- six cycles completed in 7,320 ms;
- 283,988 source frames in the test summaries;
- 2,332 writes, including partial and zero-capacity writes;
- 37 flushes;
- 91 cancellation requests;
- 120 callbacks drained;
- zero pending transfers at final summary;
- zero failed transfers and zero cancellation errors.

Two cycles directly observed the flush barrier rejecting a producer write. Four cycles completed the flush before the producer's next retry; those were correctly recorded as barrier-not-observed rather than barrier rejections.

Transport behavior on this DAC — descriptor parsing, clock configuration, payload transfers, detach handling and real-file playback — is summarized in [the validated combination](VALIDATED_COMBINATION.md).

### Configured session and detach

The recorded session enumerated the RU3, granted USB permission, opened the Android connection, read 361 descriptor bytes, created the Rust worker, parsed UAC2 topology, configured 48 kHz stereo PCM16, verified the clock read-back, then shut down. A later detach occurred while the worker was idle with no pending transfer. The close report showed the worker closed, no pending transfers, two interface releases for the configured session, handle/context closed and no cleanup error.

This is an idle-session detach/cleanup observation, not an active-transfer detach test.

## 8. UAC1 status

The RU3 product/manual information states that UAC1.0 is supported and selectable. The Rovia captures supplied for this documentation identify the device in UAC2 mode (`Cayin RU3 (U2)`) and parse UAC2 descriptors. Rovia's captures cover UAC2 only: there is no Rovia UAC1 descriptor capture, UAC1 alternate selection or UAC1 transport test. UAC1 is a device capability that Rovia has not exercised.

## 9. DSD, DoP and Raw Data

The DAC's product information advertises native DSD512 in UAC2 mode. Rovia's current Direct USB qualification scope is UAC Type I PCM. Alternate 4 is classified as UAC2 Raw Data and must not be relabeled as PCM, DSD or DoP without a separate descriptor interpretation and end-to-end implementation/verification. DSD/DoP is not currently enabled by this profile.

## 10. Device configuration that can alter output

The manual describes EQ, PCM filters, gain, output and power modes. For a sample-preservation test, a known neutral/disabled EQ setting matters, and analog gain or hardware output power is not proof of digital sample integrity.

The DAC is powered over USB according to the published manual. Host power behavior and long-session thermal/power stability are separate from PCM correctness.

## 11. What is established for this DAC

| Claim | Status |
|---|---|
| RU3 supports both UAC1 and UAC2 | Manufacturer/manual-published |
| RU3 UAC2 advertises PCM up to 32-bit/768 kHz | Manufacturer/distributor-published |
| RU3 advertises native DSD512 | Manufacturer/distributor-published |
| Rovia parsed the RU3 UAC2 descriptor topology | Rovia-observed and physically exercised |
| Rovia selected RU3 16/24/32-bit PCM alternates | Rovia-observed in hardware stress tests |
| Rovia changed/read back 44.1 and 48 kHz clocks | Rovia physically exercised |
| Rovia sent synthetic non-zero PCM payload over USB | Rovia physically exercised |
| Rovia played local FLAC files to the RU3 via Direct USB | Rovia physically exercised (24-bit at 44.1/48/96 kHz) |
| Rovia supports RU3 PCM 32/768 end-to-end | Not verified |
| Rovia supports RU3 UAC1 mode | Not yet tested |
| Rovia supports DSD/DoP through Raw Data alternate | Not supported/established |

## 12. Sources

- [Cayin RU3 manufacturer product overview](https://en.cayin.cn/features/0/124/722.html)
- [RU3 technical product listing](https://cayin.com/produkt/cayin-ru3-usb-dac-dongle/?v=7c6d99660c8a)
- [RU3 user manual reproduction](https://manuals.plus/cayin/ru3-portable-usb-dac-headphone-amplifier-manual)
- Rovia's own measurements on this DAC are summarized in [the validated combination](VALIDATED_COMBINATION.md); raw audit captures are not part of the project tree.

> HISTORICAL MEASUREMENT (2026-11-21 note) — this report measures the
> OLD platform decoder path (`c2.android.flac.decoder` / Codec2) and the
> Android AudioFlinger output route on the test device. That decoder was
> removed from all playback paths on 2026-11-21; every mode now decodes
> with Symphonia (id `symphonia-rust`). The findings below are kept as
> the evidence for that removal, not as a description of any current
> playback path.

# Android standard-path output measurements

What this report records: normal music playback through Android's
standard audio path (AudioTrack / Media3 -> AudioFlinger -> audio HAL ->
USB audio) on the test device, and how that route treats sample rate and
bit depth. It is a measurement of the standard path — the path Rovia's OFF mode
used before the Symphonia-only change of 2026-11-21 — and it is one of
the two measurements behind the decision to decode with Symphonia in
Rust in every mode: the standard route provably changes both the sample
rate and the bit depth of the audio passing through it.

# Findings: Android AudioFlinger, Audio Policy, and USB Audio Output

**Investigation date:** November 16, 2026  
**Test device:** POCO Munch running a LineageOS-based ROM (Android 13), with a Cayin RU3 (U2) USB DAC  
**Scope:** Normal audio playback through Android `AudioTrack` / Media3 → AudioFlinger → Audio HAL / Qualcomm vendor implementation → USB Audio. This investigation **does not** test Rovia's Direct USB playback path.

---

## 1. Findings Summary

On the tested device and ROM, normal music playback through Android exhibited the following behavior:

| Content / application | Source or AudioTrack sample rate | Recorded USB backend configuration | Reported DAC indicator | Supported conclusion |
|---|---:|---:|---:|---|
| Rovia, 44.1 kHz track | 44,100 Hz at the application path | 48,000 Hz, 24-bit | 48 kHz | The normal playback path changes the backend output rate to 48 kHz. |
| Rovia, 48 kHz track | 48,000 Hz according to the user's test | 48 kHz backend behavior, consistent with the 44.1 kHz log | 48 kHz | The DAC reports 48 kHz. |
| Rovia, 96 kHz track | AudioTrack is still reported at 96,000 Hz | 192,000 Hz, 16-bit (`S16_LE`) on USB Audio RX | 192 kHz | The Qualcomm vendor HAL selects a 192 kHz USB backend for this session. |
| YouTube | Normal playback | Not used to infer bit depth from the available evidence | 48 kHz | The DAC is reported to show 48 kHz. |
| Another music player, 96 kHz track | No internal dump from the other application was provided | The user reports the same DAC result | 192 kHz | This strengthens the hypothesis that the 96 → 192 kHz mapping occurs in the system/ROM path, rather than being specific to the Rovia decoder. |

**Main conclusion:** For the tested Android 13 + ROM + Cayin RU3 configuration, the observed normal playback path has two notable USB output configurations: **48 kHz** for the tested 44.1/48 kHz content paths, and **192 kHz** for the Rovia session playing 96 kHz content. Vendor logs show that the 96 kHz session was configured as **16-bit S16_LE**.

These findings must not be generalized into a claim that all Android devices only output 48/192 kHz, or that all audio on every Android version is always converted to 16-bit. The device's own dump advertises a USB profile with several other sample rates, while the 44.1 kHz path log shows a 24-bit backend.

---

## 2. Architecture of the Audio Path Under Investigation

The normal playback path examined in this report can be represented conceptually as follows:

```text
Audio file
   ↓
Media3 / ExoPlayer decoder
   ↓  Decoded PCM
Media3 AudioSink
   ↓
Android AudioTrack
   ↓
AudioFlinger / Audio Policy
   ↓
Vendor Audio HAL (Qualcomm)
   ↓
USB Audio RX / USB Audio endpoint
   ↓
Cayin RU3
```

Several rates and formats must be distinguished:

- **Source / decoder output:** The PCM format produced by the decoder from the audio file.
- **Media3 AudioSink input:** The PCM data received by the sink after AudioProcessor processing.
- **AudioTrack rate:** The sample rate requested/used by the application's stream in the Android framework.
- **AudioFlinger thread rate:** The configuration of a mixer thread or output thread. This value is not automatically the same as the USB sample rate actually selected by the vendor HAL.
- **USB backend rate and bit width:** The configuration selected by the vendor Audio HAL for the USB device. In this investigation, the `USB_AUDIO_RX SampleRate` log entry and the `S16_LE` format are important evidence for the 96 kHz session.

For this reason, the `384000 Hz` value shown for the AudioFlinger output thread **must not be interpreted as the DAC receiving 384 kHz**. In the 96 kHz session log, the AudioFlinger thread is shown at 384 kHz, but the vendor HAL explicitly configures USB Audio RX at 192 kHz.

---

## 3. Findings from the Audio Policy Dump

### 3.1 Cayin RU3 Profile Advertised to Android

The `D_rovia_policy.txt` dump lists the following device:

```text
"USB-Audio - Cayin RU3 (U2)"
AUDIO_FORMAT_PCM_32_BIT (0x3)
sampling rates: 48000, 88200, 96000, 176400, 192000, 352800, 384000
channel masks: stereo, 7.1
```

Evidence location: `D_rovia_policy.txt`, approximately lines 58–79 (`USB Headset Out` profile). The same profile is also visible in the `audio_policy.txt`, `A_idle_policy.txt`, and `B_rovia_policy.txt` snapshots.

Interpretation:

1. The Audio Policy on the tested ROM advertises the RU3 USB capabilities as PCM 32-bit, with sample rates including 48, 88.2, 96, 176.4, 192, 352.8, and 384 kHz.
2. This profile is not limited to only two sample rates. At the endpoint/policy capability declaration level, this configuration is therefore not restricted to 48 kHz and 192 kHz.
3. The advertised profile does not guarantee that every playback path will select every listed rate. Actual selection still depends on the stream, route, Audio Policy, vendor HAL implementation, and endpoint configuration.
4. The `AUDIO_FORMAT_PCM_32_BIT` declaration indicates the format advertised for the endpoint profile. It does not prove that every actual session sends 32-bit audio over USB.

### 3.2 The `hifi_playback` Path

The Audio Policy dump also contains a `hifi_playback` mix port with a dynamic profile and PCM 32-bit support for USB devices. This is relevant because the 96 kHz session in logcat enters a vendor use case named `hifi-playback`.

The `hifi_playback` name in the Audio Policy configuration is a use-case/route name defined by the ROM/vendor implementation. It is not, by itself, evidence that the stream is bit-perfect. To claim bit-perfect playback, the actual format and data delivered to the endpoint must be verified separately.

---

## 4. Findings from AudioFlinger

### 4.1 Application Stream Rate During the 96 kHz Session

In the `D_rovia_flinger.txt` dump and the subsequent `E_rovia_flinger.txt` snapshot, the AudioTrack event for the Rovia process shows `SRate 96000`.

The same dump shows one AudioFlinger output thread with the following configuration:

```text
Sample rate: 384000 Hz
HAL format: AUDIO_FORMAT_PCM_32_BIT
Processing format: AUDIO_FORMAT_PCM_FLOAT
```

Interpretation:

- The Rovia stream tracked by AudioFlinger is still reported at 96,000 Hz.
- The internal output thread shown in the dump runs with a 384,000 Hz configuration and a floating-point processing format.
- The 384,000 Hz value is the thread configuration reported by AudioFlinger; it is not proof of the USB sample rate received by the DAC.
- The vendor HAL log for the corresponding session explicitly selects 192,000 Hz for USB Audio RX. Therefore, the 192 kHz USB backend rate must be read from the vendor HAL log, not inferred from the AudioFlinger output thread sample rate.

Evidence location: `D_rovia_flinger.txt`, approximately lines 638–659, and the `E_rovia_flinger.txt` snapshot around the `AT::add` event at 11:54:15.

### 4.2 Why the AudioFlinger Dump Alone Cannot Prove the Physical USB Format

The AudioFlinger dump shows stream and thread configurations at the framework/audio-server layer. The vendor HAL may still perform rate mapping, resampling, or format conversion when configuring the device backend. Therefore:

- `AudioTrack SRate 96000` means the application stream is recorded at 96 kHz.
- `AudioFlinger Sample rate 384000 Hz` means the displayed thread is configured at 384 kHz.
- `USB_AUDIO_RX SampleRate set to KHZ_192` means the vendor HAL configures the USB RX backend at 192 kHz.

These are three different layers and must not be treated as equivalent.

---

## 5. Findings from Qualcomm Vendor Logcat

### 5.1 96 kHz Source → 192 kHz / 16-bit USB Backend

In `rovia_usb_audit_logcat.txt`, while Rovia is playing a 96 kHz track, the log records the `hifi-playback` use case and selection of the `USB_HEADSET` device.

The important entries around 11:54:15 show the following sequence:

```text
start_output_stream ... usecase(16: hifi-playback)
...
platform_check_and_set_codec_backend_cfg ... bitwidth 16, samplerate 384000
...
updated afe: bitwidth 16, samplerate 192000
Override USB BE configured as bit_width(16)sample_rate(192000)
Codec selected backend ... updated bit width: 16 and sample rate: 192000
...
USB_AUDIO_RX Format mixer set to (S16_LE)16 bit(local)
...
USB_AUDIO_RX SampleRate set to KHZ_192
```

Evidence location: `rovia_usb_audit_logcat.txt`, approximately lines 13548–13605.

Interpretation:

1. At the beginning of configuration, the log shows an AFE value of 384 kHz.
2. The vendor platform then updates the selected backend configuration to 192 kHz.
3. The vendor HAL records `USB_AUDIO_RX SampleRate set to KHZ_192`.
4. The USB Audio RX mixer format is recorded as `S16_LE`, which means signed 16-bit little-endian.
5. This is strong evidence that, during the tested normal 96 kHz session, the Qualcomm vendor HAL configures the USB backend for 192 kHz and 16-bit audio.

Evidence limitation: This log proves the backend configuration performed by the HAL. It is not a USB protocol analyzer or a physical USB packet capture, so it does not prove the exact bitstream/payload received by the DAC for every sample. The 192 kHz DAC indicator comes from the user's direct observation and is consistent with the HAL configuration.

### 5.2 44.1 kHz Source → 48 kHz / 24-bit USB Backend

Around 11:53:00 in `rovia_usb_audit_logcat.txt`, while Rovia is playing a 44.1 kHz track, the log shows the `deep-buffer-playback` use case and the USB headset output device.

Important entries:

```text
start_output_stream ... usecase(0: deep-buffer-playback)
...
platform_check_and_set_codec_backend_cfg ... bitwidth 24, samplerate 48000
...
set default Sample Rate(48k)
...
updated afe: bitwidth 24, samplerate 48000
Override USB BE configured as bit_width(24)sample_rate(48000)
```

Evidence location: `rovia_usb_audit_logcat.txt`, approximately lines 4905–4942.

Interpretation:

- The vendor backend configures the 44.1 kHz path at 48 kHz.
- The same log specifies a 24-bit width, not 16-bit.
- This is the main reason this report does not conclude that every track is always converted to 16-bit. The recorded bit-width behavior differs between the 44.1 kHz and 96 kHz sessions.

### 5.3 Observations Across Applications

The user reported the following DAC observations:

- Rovia 44.1 kHz → DAC 48 kHz.
- Rovia 48 kHz → DAC 48 kHz.
- Rovia 96 kHz → DAC 192 kHz.
- YouTube → DAC 48 kHz.
- Another music player playing a 96 kHz track → DAC 192 kHz.

The fact that another music player also displays 192 kHz when playing a 96 kHz source supports the hypothesis that the 96 → 192 kHz mapping is performed by the system/ROM/vendor HAL path, rather than by behavior specific to the Rovia decoder. However, without an internal dump from the other application or a USB bus capture, this remains a strong inference, not proof of the vendor's internal code.

---

## 6. Findings from the Media3 PCM Capture

The metadata file `rovia_phase5_pcm_1794804070458.json` states that the capture was performed at:

```text
capturePoint: Media3 AudioSink input buffers
captureMethod: Bytes advanced by the delegated AudioSink handleBuffer call
```

The recorded formats are:

| Layer | Sample rate | Encoding |
|---|---:|---|
| Renderer input / FLAC decoder output | 96,000 Hz | PCM 24-bit packed |
| Media3 AudioSink input | 96,000 Hz | PCM 16-bit |
| AudioTrack | 96,000 Hz | PCM 16-bit |

The metadata also records:

- Decoder: `c2.android.flac.decoder`
- Channel count: 2
- The capture was not truncated (`truncated: false`).
- No sink format change occurred during the capture.
- `bufferSize: 192000` in the AudioTrack metadata is a buffer size in **bytes**, not a 192 kHz sample rate.

Interpretation:

1. For this 96 kHz FLAC capture, the decoder outputs packed 24-bit PCM.
2. Before or by the time the PCM reaches the AudioSink, the recorded format has already become 16-bit PCM.
3. The recorded AudioTrack also uses 16-bit PCM at 96 kHz.
4. This evidence is consistent with the vendor HAL log configuring USB Audio RX as S16_LE during the 96 kHz session.

Important evidence limitation: This capture was taken at the Media3 AudioSink input, **not** from the USB cable, a DAC loopback, or a USB packet capture. Therefore, it confirms a format change at the Media3 AudioSink/AudioTrack layer, but it does not independently prove the physical payload on the USB bus.

---

## 7. What the Official Android and Media3 Documentation States

This section separates Android API capabilities from the behavior specific to the tested ROM and vendor HAL.

### 7.1 Android 6.0 (API 23) and Sample Rate Support up to 192 kHz

The Android `AudioFormat` documentation states that, starting with `Build.VERSION_CODES.M` (API 23 / Android 6.0), `AudioTrack` and `AudioRecord` support sample rates up to 192 kHz. The same documentation explains that the sample rate in `AudioFormat` represents the input content rate; Android may perform sample-rate conversion (SRC) to match the output in use. The documentation also recommends that applications follow the endpoint's rate and generally not exceed 48 kHz unless the endpoint has specific capabilities.

Source:

- [Android API Reference — AudioFormat](https://developer.android.com/reference/android/media/AudioFormat)

Consequences:

- The Android framework has supported sample rates up to 192 kHz at the API level since Android 6.0.
- This does not guarantee that every Android device, codec, Audio HAL, or USB endpoint will accept and output 192 kHz.
- Framework capabilities, codec capabilities, endpoint profiles, and vendor HAL configuration are separate layers.

### 7.2 Codec Sample Rate Support Is Codec-Specific

The `MediaCodecInfo.AudioCapabilities` documentation provides methods such as:

- `getSupportedSampleRates()`
- `getSupportedSampleRateRanges()`
- `isSampleRateSupported(int sampleRate)`

This means supported rates must be checked for the codec actually being used. There is no basis for concluding that all Android decoders only support 48 kHz and 192 kHz, or that all Android codecs share the same limits.

Source:

- [Android API Reference — MediaCodecInfo.AudioCapabilities](https://developer.android.com/reference/android/media/MediaCodecInfo.AudioCapabilities)

### 7.3 Android PCM Is Not Always 16-bit

The `AudioFormat` documentation includes several PCM encodings, including PCM 16-bit, PCM float, packed PCM 24-bit, and PCM 32-bit integer. The documentation notes that packed PCM 24-bit and PCM 32-bit integer encodings were added in API 31. Therefore, 16-bit is not the only PCM representation recognized by Android.

Source:

- [Android API Reference — AudioFormat](https://developer.android.com/reference/android/media/AudioFormat)

It is important to distinguish API encoding support from the actual support provided by a device and its audio path. An encoding may be available in the framework, but that does not mean it will be selected or supported by every combination of codec, AudioSink, HAL, and endpoint.

### 7.4 Default Behavior of Media3 `DefaultAudioSink`

The Media3 documentation explains that `DefaultAudioSink.Builder` can be configured with AudioProcessors and output settings. The `setEnableFloatOutput` option controls whether PCM float output is used. The `DefaultAudioSink` implementation on the reviewed Media3 `release` branch selects a 16-bit integer PCM conversion path when float output is not enabled; when float output is enabled and the format/device support it, the float path can be used for high-resolution PCM.

Sources:

- [AndroidX Media3 API — DefaultAudioSink.Builder](https://developer.android.com/reference/androidx/media3/exoplayer/audio/DefaultAudioSink.Builder)
- [AndroidX Media source — DefaultAudioSink.java](https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/DefaultAudioSink.java)

Implications for this investigation:

- The included Media3 capture does show PCM24 from the decoder becoming PCM16 at the AudioSink input for the recorded session.
- This is consistent with the default Media3 conversion path examined.
- However, implementation details must always be checked against the exact Media3 version used by the project and that project's builder/AudioProcessor configuration. This is not evidence that all Android applications or all playback paths are required to use 16-bit audio.

### 7.5 AOSP USB Audio Documentation

The general AOSP USB Audio documentation describes the minimum subset of USB Audio Class validated by the Android reference implementation. The documented UAC1 section lists common rates up to 48 kHz and states that not all advanced features are validated or guaranteed by that document.

Source:

- [AOSP — USB Digital Audio](https://source.android.com/docs/core/audio/usb)

This general documentation must not be treated as the maximum capability of every Android device. OEM/vendor implementations may provide broader USB Audio Policy/HAL configurations. In this actual investigation, the Cayin RU3 policy dump advertises several rates above 48 kHz, including 96, 192, 352.8, and 384 kHz.

### 7.6 Android 14 / API 34: Preferred Mixer Attributes and Bit-Perfect Playback

Android 14 introduced mixer attributes APIs that allow applications to request specific mixer behavior for supported usages and devices. `MIXER_BEHAVIOR_BIT_PERFECT` describes mixer behavior that does not alter data through mixing; support still depends on the device, HAL, format, and route.

Sources:

- [Android API Reference — AudioMixerAttributes](https://developer.android.com/reference/android/media/AudioMixerAttributes)
- [Android API Reference — AudioManager](https://developer.android.com/reference/android/media/AudioManager)
- [Android guide — Improve audio playback](https://developer.android.com/media/platform/improve-audio-playback)

The test device in this investigation runs Android 13 (API 33), so the Android 14 mixer attributes APIs must not be assumed to be available on the tested OS. This feature is also separate from the dedicated Rovia Direct USB implementation currently being planned.

---

## 8. Answers to the Two Main Hypotheses

### Hypothesis A — “Android 6 and later support up to 192 kHz.”

**Correct as a framework API support limit for `AudioTrack`/`AudioRecord`, with qualifications.** Android documentation states support up to 192 kHz starting with API 23 / Android 6.0. However, this does not guarantee 192 kHz output on every device, nor is it a single limit that defines every audio path. The codec, hardware, USB endpoint, Audio Policy, and vendor HAL still determine the actual configuration.

### Hypothesis B — “All tracks will be converted to 16-bit.”

**This is not true as a universal Android rule.** The following three pieces of evidence must be considered together:

1. Rovia's 96 kHz capture: the decoder outputs packed PCM 24-bit, while Media3 AudioSink and AudioTrack are recorded as receiving/using PCM 16-bit.
2. Qualcomm log for the 96 kHz session: USB Audio RX is configured as `S16_LE` / 16-bit at 192 kHz.
3. Qualcomm log for the 44.1 kHz session: the USB backend is configured as 24-bit at 48 kHz.

Therefore, the evidence supports this statement: **during the tested normal 96 kHz session, the Media3 path and vendor USB backend show 16-bit PCM output; during the tested normal 44.1 kHz session, the vendor log shows a 24-bit backend.** There is no evidence that all tracks, all sample rates, all codecs, and all Android devices are always converted to 16-bit.

---

## 9. Conclusions for the Rovia Architecture

For normal playback without the dedicated Direct USB path, the conclusions from this device test are:

1. Rovia/AudioTrack can continue to report 96 kHz content at the application stream level.
2. During the tested session, the Qualcomm vendor HAL selects a 192 kHz, 16-bit USB backend.
3. The 44.1 kHz path selects a 48 kHz backend, and the log records a 24-bit width.
4. Audio Policy advertises several RU3 endpoint capabilities, not only 48 kHz and 192 kHz.
5. The DAC's 192 kHz indicator for 96 kHz content does not prove bit-perfect playback or preservation of 24-bit resolution. It is consistent with conversion/up-sampling performed in the system/vendor path.
6. The 96 → 192 kHz behavior also observed in another music player strengthens the hypothesis that the decision occurs in the ROM/vendor HAL path, but the vendor's internal algorithm cannot be established from logs alone.

**Design implication:** Rovia's decoder should continue to output PCM according to the decoded source and should not convert 96 kHz to 192 kHz merely to imitate the DAC display observed on this device's normal playback path. Sample rate and bit depth should be recorded as separate values at each pipeline boundary: `decoderOutputFormat`, `audioSinkInputFormat`, `audioTrackFormat`, `audioFlingerThreadFormat`, and `usbBackendFormat`.

Rovia's Direct USB development must be evaluated as a separate path. This report does not claim that Direct USB is already operational, does not measure the physical USB payload, and does not draw bit-perfect conclusions about a path that has not yet been tested.

---

## 10. Limitations and Evidence Confidence

| Statement | Evidence status |
|---|---|
| Android `AudioTrack` / `AudioRecord` APIs support up to 192 kHz starting with Android 6.0 / API 23 | Supported by official Android documentation. |
| All Android codecs support the same sample rates | Incorrect; capabilities are codec-specific. |
| The RU3 on the tested ROM advertises PCM32 with selected rates from 48–384 kHz | Supported by the Audio Policy dump. |
| Rovia's AudioTrack is recorded at 96 kHz during the 96 kHz session | Supported by the AudioFlinger dump. |
| The AudioFlinger output thread shows 384 kHz | Supported by the AudioFlinger dump; this is not the USB rate received by the DAC. |
| The vendor HAL configures USB Audio RX at 192 kHz, S16_LE, for Rovia's 96 kHz session | Supported by the Qualcomm vendor logcat. |
| The vendor HAL configures the USB backend at 48 kHz, 24-bit, for Rovia's 44.1 kHz session | Supported by the Qualcomm vendor logcat. |
| The DAC receives USB payloads exactly matching that configuration in every packet | Not yet verified with a USB protocol capture / hardware loopback. |
| All Android audio is always converted to 16-bit | Not supported; there is evidence of a 24-bit path. |
| All Android devices map 96 kHz to 192 kHz | Not supported; this is a finding specific to the tested device/ROM/vendor configuration. |
| Rovia's Direct USB path is already bit-perfect | Outside the scope of this investigation; not demonstrated by this test. |

---

## 11. Evidence Files Used

### Logcat and PCM Metadata

- `rovia_usb_audit_logcat.txt` — Qualcomm backend configuration logs while Rovia plays 44.1 kHz and 96 kHz content.
- `logcat(20261116-044152).txt` — system log captured when the USB audio device was detected. It contains `UsbAlsaManager: alsaFileAdded(pcmC0D0p)` and the addition of ALSA card 0. This proves that the system detected the USB audio ALSA node; it does not prove that Rovia Direct USB is operating or establish the actual stream format.
- `rovia_phase5_pcm_1794804070458.json` — metadata for the 96 kHz Media3 PCM capture, comparing decoder-output PCM24 with PCM16 at the AudioSink input and AudioTrack.
- `rovia_phase5_pcm_1794804070458.pcm` — raw data for the capture referenced by the metadata. The capture point remains the Media3 AudioSink, not the USB bus.

### AudioFlinger Dumps

- `audio_flinger.txt` — initial AudioFlinger dump.
- `A_idle_flinger.txt` — DAC connected and idle state.
- `B_rovia_flinger.txt` — snapshot while Rovia plays 44.1 kHz content.
- `C_youtube_flinger.txt` — snapshot while YouTube is playing.
- `D_rovia_flinger.txt` — snapshot while Rovia plays 96 kHz content.
- `E_rovia_flinger.txt` — additional snapshot of the Rovia 96 kHz session at approximately 11:54.

### Audio Policy Dumps

- `audio_policy.txt` — initial Audio Policy dump.
- `A_idle_policy.txt` — DAC connected and idle state.
- `B_rovia_policy.txt` — snapshot while Rovia plays 44.1 kHz content.
- `C_youtube_policy.txt` — snapshot while YouTube is playing.
- `D_rovia_policy.txt` — snapshot while Rovia plays 96 kHz content.
- `E_rovia_policy.txt` — additional snapshot of the Rovia session.

Snapshots A–E represent the device state at the time each dump was taken. A snapshot captured after playback has stopped or a thread has entered standby must not be used as proof that a stream was active at that time. For this reason, the main rate/format findings in this report prioritize vendor logcat events with timestamps and explicit configuration-change entries.

---

## 12. Documentation References

1. [Android `AudioFormat`](https://developer.android.com/reference/android/media/AudioFormat) — content sample rate, Android SRC, the 192 kHz limit at API M/API 23, and PCM encodings.
2. [Android `MediaCodecInfo.AudioCapabilities`](https://developer.android.com/reference/android/media/MediaCodecInfo.AudioCapabilities) — how to check the sample rates supported by each codec.
3. [AndroidX Media3 `DefaultAudioSink.Builder`](https://developer.android.com/reference/androidx/media3/exoplayer/audio/DefaultAudioSink.Builder) — AudioSink configuration and float output.
4. [AndroidX Media source — `DefaultAudioSink.java` (release branch)](https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/DefaultAudioSink.java) — implementation of PCM16 or float output path selection on the reviewed branch.
5. [AOSP — USB Digital Audio](https://source.android.com/docs/core/audio/usb) — the scope of generic Android USB Audio support and the limits of the UAC1 subset validated in the reference documentation.
6. [Android `AudioMixerAttributes`](https://developer.android.com/reference/android/media/AudioMixerAttributes) — mixer behavior, including `MIXER_BEHAVIOR_BIT_PERFECT` on API 34.
7. [Android `AudioManager`](https://developer.android.com/reference/android/media/AudioManager) — preferred mixer attributes APIs and their application on supported devices/usages.
8. [Android guide — Improve audio playback](https://developer.android.com/media/platform/improve-audio-playback) — using mixer attributes to control playback behavior.

---

**Final statement:** Android documentation supports framework sample rates up to 192 kHz starting with Android 6.0 (API 23), but output rate and bit-depth selection are not governed by one universal policy across all devices. On the tested POCO/ROM + Cayin RU3 configuration, the normal playback path shows a 48 kHz backend for 44.1 kHz content and a 192 kHz backend for the 96 kHz session. For that 96 kHz session, both the Media3 and vendor HAL evidence indicate PCM 16-bit; however, the 44.1 kHz session shows a 24-bit backend. Therefore, the claim that “all tracks are always converted to 16-bit” cannot be generalized.

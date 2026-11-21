# Diagnostics Captures

## What the captures are

The two reports in this directory were captured on the test device
(POCO F4 + Cayin RU3) from Rovia's standard (OFF) playback path, using a
development build's audio diagnostics: a capture sink on the ExoPlayer
audio output recorded the decoder identity, the decoder's output format
and the exact PCM buffers handed to the AudioTrack, alongside logcat from
the audio HAL and AudioFlinger.

The diagnostics tooling itself was development-only and is not part of
the app. The reports are kept because their findings are the design
input for the Bit-Perfect modes:

- [result_c2_android_flac_decoder.md](archive/result_c2_android_flac_decoder.md) (archived 2026-11-21 — the platform decoder it measures was removed from all modes):
  FLAC sources are 24-bit and the renderer input is PCM 24-bit, but what
  reaches the AudioSink and AudioTrack is 16-bit PCM, at both 48 kHz and
  96 kHz — the standard Android route truncates 24-bit audio on this
  device. The report also records a controlled experiment with the
  decoder's Float32 output.
- [Result_Android_AudioFlinger.md](Result_Android_AudioFlinger.md): the
  system output route behind the app resamples and reconfigures the USB
  backend (48 kHz for 44.1/48 kHz content; 192 kHz / 16-bit for a 96 kHz
  session).

## How to read them

Decoder identity and PCM observations from the Android path describe the
Android path only. They say nothing about Symphonia's output, which is
verified by the Rust host tests and by physical playback in the
Bit-Perfect modes themselves. Each capture names its mode, decoder,
source format and output format; a capture made in one mode is never
used to describe another.

# Verification

Measurements of how audio actually moved through the standard Android
path on the test device, and the per-mode state of the Bit-Perfect
backends. The two measurement reports below are the reason the
Bit-Perfect modes decode with Symphonia in Rust — and, since
2026-11-21, why the platform decoder was removed from every mode,
OFF included (see the decoder policy).

- [Android AudioFlinger result](Result_Android_AudioFlinger.md) — the
  standard output route resamples and reconfigures the USB backend on the
  test device (44.1/48 kHz content leaves at 48 kHz; a 96 kHz session is
  configured as 192 kHz / 16-bit by the vendor HAL).
- [c2.android.flac.decoder result](archive/result_c2_android_flac_decoder.md) —
  archived 2026-11-21: 24-bit FLAC decoded by the (since removed)
  platform FLAC decoder reached the Android audio output as 16-bit PCM;
  the captures also document the decoder's Float32 output behavior
  under a controlled experiment.
- [Diagnostics](DIAGNOSTICS.md) — how the captures were made and how to
  read them.
- [Validation matrix](VALIDATION_MATRIX.md) — what each output mode does
  and its state on the test hardware.

Synthetic PCM transport tests, Android decoder captures, Symphonia host
tests and physical local-file playback are different things; the records
keep them separate. The platform-decoder captures above are historical
evidence for the Symphonia-only change, not measurements of any
current playback path.

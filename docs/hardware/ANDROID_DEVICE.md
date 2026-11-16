# Android Host Device: Xiaomi POCO F4 (`munch`)

## 1. Device identity

| Property | Value | Source |
|---|---|---|
| Manufacturer | Xiaomi | Manufacturer specification and user device identity |
| Product | POCO F4 | User-reported; model family listed by Xiaomi |
| Device codename | `munch` | Device metadata source; also user-reported |
| SoC | Qualcomm Snapdragon 870 / SM8250-AC | Xiaomi product specification and device metadata |
| CPU | Kryo 585, octa-core | Xiaomi product specification / device metadata |
| GPU | Qualcomm Adreno 650 | Xiaomi product specification |
| USB OTG | Supported by device metadata; host connection physically exercised | Device metadata and Rovia USB audit |

Xiaomi lists the Snapdragon 870 and Adreno 650 in the POCO F4 specifications. Device metadata identifies POCO F4 as `munch` and lists USB OTG. The actual Rovia audit independently demonstrates that this phone opened a USB host connection to the RU3 and passed its file descriptor to the Rust backend.

## 2. Installed software profile

The following values were supplied by the user for the test device:

| Property | User-reported value |
|---|---|
| ROM | AOSP-based custom ROM |
| Android version | Android 16 |
| Build ID | `BP4A.251205.006` |
| Security patch level | 1 March 2026 |

These exact ROM/build/security-patch values were not extracted from `getprop` output in the supplied audit capture. Treat them as the user's stated current device profile until a property dump is recorded.

## 3. Role in Rovia development

The POCO F4 is the current Android physical test host for:

- Android USB host permission and connection;
- descriptor acquisition and device detach events;
- UniFFI session creation and close ordering;
- Rust `arm64-v8a` backend loading;
- UAC2 descriptor and clock operations;
- asynchronous USB transfer and stress validation;
- Media3/ExoPlayer decoder coordination and Android output regression testing;
- Native Android 14 mode HAL capability inspection;
- DAP Mode stream validation.

## 4. Recorded runtime behavior

The device audit records:

- a single connected audio USB device;
- USB permission granted;
- `UsbDeviceConnection` opened with a file descriptor;
- a 361-byte raw descriptor block read;
- the Rust backend linked for `android/aarch64` and libusb `1.0.27`;
- successful session creation and descriptor diagnostics;
- successful UAC2 clock read-back at 48 kHz after configuration;
- orderly worker shutdown and Android connection close.

This is the application-level USB host path working on the recorded device/ROM state. It says nothing about Native Android bit-perfect Audio HAL support (the HAL on this device does not expose it) or decoder precision.

## 5. USB power and field testing

The attached portable DAC is powered over USB according to its product documentation. Battery/power behavior, thermal behavior, host current limits and long-duration stability should be measured separately if they become relevant. Do not infer high-rate capability solely from a successful USB enumeration.

## 6. Device specification links

- [Xiaomi POCO F4 specifications](https://www.mi.co.id/id/product/poco-f4/specs/)
- [Device metadata identifying `munch`](https://github.com/LineageOS/lineage_wiki/blob/main/_data/devices/munch_variant1.yml)
- [Android 16 platform overview](https://developer.android.com/about/versions/16)

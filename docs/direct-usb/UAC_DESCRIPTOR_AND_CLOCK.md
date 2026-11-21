# UAC Descriptor Parsing and Clock Configuration

## 1. Descriptor-driven design

Rovia must discover interfaces, alternate settings, endpoints, terminal links, clock entities and PCM formats from the connected device descriptors. It must not hardcode an interface, endpoint or alternate based only on a product name.

Parsing must use strict descriptor length/bounds checks. Reject malformed, missing, ambiguous, cyclic or unsupported relationships.

## 2. Required relationships

- Resolve AudioControl and AudioStreaming interfaces.
- Resolve the AudioStreaming terminal link to its corresponding AudioControl terminal.
- Traverse the Clock Source, Clock Selector and Clock Multiplier topology when present.
- Associate the streaming data endpoint and feedback endpoint with the selected alternate.
- Keep endpoint direction, transfer type, sync type, usage, packet size, interval and feedback relationship separate.
- Keep OUT data endpoint scheduling separate from feedback endpoint polling interval.

## 3. PCM and Raw Data

Direct USB's current primary scope is UAC Type I PCM. UAC2 Raw Data must not be assumed to mean PCM, DSD or DoP. Unsupported audio class versions, formats or topologies fail closed until their parser and transport are implemented and tested.

## 4. Precision and subslot

Keep source significant bit depth, decoded precision, memory container width, UAC bit resolution and USB subslot width distinct.

The current native PCM selection accepts source precision requests 16, 24 and 32 bits and rejects unsupported precision such as 20-bit. Select the smallest advertised compatible subslot that carries the actual decoded precision. For the tested RU3 profile, the observed candidates are:

| Alternate | Channels | UAC resolution | Subslot |
|---:|---:|---:|---:|
| 1 | 2 | 16-bit | 2 bytes |
| 2 | 2 | 24-bit | 3 bytes |
| 3 | 2 | 32-bit | 4 bytes |
| 4 | Raw Data | Not treated as PCM | 4-byte descriptor subslot is not proof of PCM |

## 5. UAC2 Clock Source requests

Current request shape used in the project:

| Field | GET_CUR | SET_CUR |
|---|---|---|
| `bmRequestType` | `0xA1` | `0x21` |
| `bRequest` | `0x01` | `0x01` |
| `wValue` | `0x0100` | `0x0100` |
| `wIndex` | `(clockSourceId << 8) | controlInterface` | same |
| `wLength` | 4 | 4 |
| Payload | little-endian `u32` current sample rate | little-endian `u32` requested sample rate |

The current timeout is 500 ms.

## 6. Mandatory configuration order

1. Validate the PCM candidate, channel count, alternate and endpoint capacity.
2. Ensure the relevant AudioControl and AudioStreaming interfaces are owned by the active session.
3. Select AudioStreaming alternate 0.
4. Read current clock with `GET_CUR`.
5. Skip `SET_CUR` when the rate already matches.
6. Issue `SET_CUR` only when the control is host-programmable.
7. Read `GET_CUR` again and require an exact match.
8. Select the desired nonzero streaming alternate.
9. Start transport only after all configuration steps succeed.

A successful `SET_CUR` without exact read-back is not proof that the device clock changed. Do not infer a supported-rate list from an unsuccessful or unsupported `GET_RANGE`.

## 7. Packet capacity

The required bytes for one service interval depend on actual sample rate, channel count, subslot bytes, bus speed and endpoint interval. Calculate each packet against the selected OUT endpoint's capacity. Reject a format when it cannot fit the descriptor-derived endpoint schedule.

For Full-Speed isochronous endpoints the frame basis is 1 ms. For High-Speed it is 125 µs; apply the isochronous interval exponent `2^(bInterval - 1)`. Do not use a feedback endpoint's `bInterval` to schedule OUT data.

SuperSpeed is rejected until companion descriptor fields and `wBytesPerInterval` are parsed and used in scheduling.

## 8. Tested RU3 configuration

The current Rovia physical capture shows UAC2, AudioControl interface 1, AudioStreaming interface 2, Clock Source entity 1, OUT endpoint `0x07`, feedback IN endpoint `0x83`, and PCM alternate settings 1/2/3. The initial clock was 768000 Hz; a test successfully set/read back 48000 Hz. A prior test also read back 44100 Hz.

These are tested configurations, not proof that every advertised sample rate is supported with every precision or alternate.

## 9. Displayed USB Audio Class label

The class label shown in the AudioPipeline sheet and the AudioOutput sheet names the mode the device is operating in. A device that declares its mode in the product string names the label directly: the Cayin RU3 enumerates as "Cayin RU3 (U1)" in its UAC1 mode and "Cayin RU3 (U2)" in its UAC2 mode while the interface protocols in its descriptors stay at the UAC2 values in both modes, and an explicit "(U1)"/"(U2)"/"(U3)" marker in the product name takes precedence over every descriptor-derived label. Without a name marker, a Direct USB session reads the active configuration from its open connection with the standard GET_CONFIGURATION request and takes the class version from that configuration, preferring the protocol of its playback AudioStreaming interface and falling back to its AudioControl protocol. Outside a Direct USB session, the label is stated only when every configuration the device offers agrees on a single class version.

The descriptor data behind a UsbDevice spans every configuration the device offers, and its flattened interface list covers only the first configuration. A first-match read of that list therefore names the first configuration's class version, not the active mode: a device operating in its UAC1 configuration still reports UAC2 when its first configuration carries a UAC2 AudioControl interface.

## 10. Planned update: active-configuration probe outside Direct USB

Label resolution extends to an active-configuration probe outside a Direct USB session when a product provides no mode marker in its product name and the descriptor-derived label still does not match the mode the device operates in. Under that condition, the AudioOutput sheet opens a short-lived connection to a device the application already holds USB permission for, reads the active configuration with GET_CONFIGURATION, takes the class version from that configuration, and closes the connection again, so the label names the operating mode in the system modes (OFF, Native A14 and DAP) as well. Until a product demonstrates that failure, the descriptor-derived resolution in section 9 remains the behaviour outside Direct USB, and an ambiguous device reports no label rather than a guessed one.

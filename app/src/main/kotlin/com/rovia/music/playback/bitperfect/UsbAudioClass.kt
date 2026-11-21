package com.rovia.music.playback.bitperfect

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface

/**
 * Resolves the USB Audio Class version a device is actually using.
 *
 * A UsbDevice's descriptor data is static: it lists every configuration
 * the device offers, and the flattened interface list
 * (UsbDevice.getInterface) covers only the first configuration. A DAC
 * that offers more than one configuration, or more than one audio
 * function, can therefore carry a UAC2 AudioControl interface in its
 * descriptors while the active configuration runs UAC1. Reading the
 * first AudioControl protocol in the flattened list pins the label to
 * that first entry regardless of the mode in use.
 *
 * Some devices declare their operating mode in the product string
 * instead of changing the class protocol their descriptors advertise:
 * the Cayin RU3 enumerates as "Cayin RU3 (U1)" in its UAC1 mode and
 * "Cayin RU3 (U2)" in its UAC2 mode while the interface protocols in
 * its descriptors stay at the UAC2 values, and Cayin's own application
 * reads the mode from that name. An explicit "(U1)"/"(U2)"/"(U3)"
 * marker in the product name therefore names the operating mode and
 * takes precedence over every descriptor-derived label.
 *
 * Without a name marker, the label is resolved from the active
 * configuration whenever a live connection can report it
 * (GET_CONFIGURATION). Without a connection, the label is reported
 * only when every configuration of the device agrees on a single
 * class version; an ambiguous device yields null instead of a
 * guessed label.
 */
internal object UsbAudioClass {

    // Standard device request GET_CONFIGURATION (USB 2.0 spec, 9.4.2):
    // bmRequestType 0x80 (device-to-host, standard, device recipient),
    // bRequest 0x08, wValue 0, wIndex 0, one data byte holding the
    // active bConfigurationValue.
    private const val REQUEST_TYPE_DEVICE_IN = 0x80
    private const val REQUEST_GET_CONFIGURATION = 0x08
    private const val CONTROL_TIMEOUT_MS = 1_000

    fun labelForProtocol(protocol: Int): String? = when (protocol) {
        0x00 -> "UAC1"
        0x20 -> "UAC2"
        0x30 -> "UAC3"
        else -> null
    }

    private val productModeMarker = Regex("""\(U([123])\)""")

    /**
     * The class version declared by an explicit mode marker in the
     * product name, such as the "(U1)" in "Cayin RU3 (U1)", or null
     * when the name carries no marker.
     */
    fun labelFromProductName(productName: String?): String? {
        val name = productName?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val marker = productModeMarker.find(name) ?: return null
        return when (marker.groupValues[1]) {
            "1" -> "UAC1"
            "2" -> "UAC2"
            "3" -> "UAC3"
            else -> null
        }
    }

    /**
     * The class version for a device: the product name's mode marker
     * when it carries one, otherwise the descriptor-derived label.
     */
    fun labelForDevice(device: UsbDevice): String? =
        labelFromProductName(device.productName) ?: bestEffortLabel(device)

    fun hasAudioFunction(device: UsbDevice): Boolean {
        for (configurationIndex in 0 until device.configurationCount) {
            val configuration = runCatching {
                device.getConfiguration(configurationIndex)
            }.getOrNull() ?: continue
            for (interfaceIndex in 0 until configuration.interfaceCount) {
                val iface = configuration.getInterface(interfaceIndex)
                if (iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO &&
                    (iface.interfaceSubclass == 1 || iface.interfaceSubclass == 2)
                ) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * The class version of one configuration: the protocol of its
     * playback AudioStreaming interface (an alternate that carries an
     * OUT endpoint) when one is present, otherwise the protocol of its
     * AudioControl interface.
     */
    fun labelInConfiguration(device: UsbDevice, configurationIndex: Int): String? {
        val configuration = runCatching {
            device.getConfiguration(configurationIndex)
        }.getOrNull() ?: return null
        val interfaces = (0 until configuration.interfaceCount).map { index ->
            configuration.getInterface(index)
        }
        return labelInInterfaces(interfaces)
    }

    /**
     * The class version of the configuration the device is currently
     * operating in, read from the live connection. Returns null when
     * the device does not answer GET_CONFIGURATION or the reported
     * configuration value matches none of its configurations.
     */
    fun labelFromActiveConfiguration(
        connection: UsbDeviceConnection,
        device: UsbDevice,
    ): String? {
        val buffer = ByteArray(1)
        val transferred = runCatching {
            connection.controlTransfer(
                REQUEST_TYPE_DEVICE_IN,
                REQUEST_GET_CONFIGURATION,
                0,
                0,
                buffer,
                buffer.size,
                CONTROL_TIMEOUT_MS,
            )
        }.getOrDefault(-1)
        if (transferred != 1) return null
        val activeValue = buffer[0].toInt() and 0xFF
        if (activeValue == 0) return null
        for (configurationIndex in 0 until device.configurationCount) {
            val configuration = runCatching {
                device.getConfiguration(configurationIndex)
            }.getOrNull() ?: continue
            if (configuration.id == activeValue) {
                return labelInConfiguration(device, configurationIndex)
            }
        }
        return null
    }

    /**
     * The class version when it can be stated without a live
     * connection: the single version every configuration of the device
     * agrees on, or null when the configurations disagree.
     */
    fun bestEffortLabel(device: UsbDevice): String? {
        val labels = LinkedHashSet<String>()
        for (configurationIndex in 0 until device.configurationCount) {
            labelInConfiguration(device, configurationIndex)?.let(labels::add)
        }
        if (labels.isEmpty()) {
            val flattened = (0 until device.interfaceCount).map { index ->
                device.getInterface(index)
            }
            labelInInterfaces(flattened)?.let(labels::add)
        }
        return labels.singleOrNull()
    }

    private fun labelInInterfaces(interfaces: List<UsbInterface>): String? {
        var controlLabel: String? = null
        for (iface in interfaces) {
            if (iface.interfaceClass != UsbConstants.USB_CLASS_AUDIO) continue
            when (iface.interfaceSubclass) {
                2 -> if (hasOutEndpoint(iface)) {
                    labelForProtocol(iface.interfaceProtocol)?.let { return it }
                }
                1 -> if (controlLabel == null) {
                    controlLabel = labelForProtocol(iface.interfaceProtocol)
                }
            }
        }
        return controlLabel
    }

    private fun hasOutEndpoint(iface: UsbInterface): Boolean {
        for (endpointIndex in 0 until iface.endpointCount) {
            if (iface.getEndpoint(endpointIndex).direction == UsbConstants.USB_DIR_OUT) {
                return true
            }
        }
        return false
    }
}

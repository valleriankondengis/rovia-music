package com.rovia.music.playback.bitperfect.direct

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.ParcelFileDescriptor
import com.rovia.music.playback.bitperfect.UsbAudioClass
import com.rovia.music.uniffi.DacVolumeDto
import com.rovia.music.uniffi.PlaybackStatusDto
import com.rovia.music.uniffi.SourceInfoDto
import com.rovia.music.uniffi.UsbDirectSession
import com.rovia.music.uniffi.UsbTransportStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal data class DirectUsbDeviceItem(
    val deviceId: Int,
    val displayName: String,
    val manufacturerName: String,
    val vendorId: Int,
    val productId: Int,
    val deviceVersion: String,
    val interfaceCount: Int,
    val permissionGranted: Boolean,
)

internal data class DirectUsbControllerState(
    val devices: List<DirectUsbDeviceItem> = emptyList(),
    val isScanning: Boolean = false,
    val isConnecting: Boolean = false,
    val selectedDeviceId: Int? = null,
    val statusMessage: String = "Scan for an attached USB Audio device",
    val errorMessage: String? = null,
)

internal class DirectUsbController(context: Context) : AutoCloseable {
    private data class PendingPermission(
        val requestId: Int,
        val result: CompletableDeferred<Boolean>,
    )

    private val applicationContext = context.applicationContext
    private val usbManager = applicationContext.getSystemService(UsbManager::class.java)
    private val permissionRequests = ConcurrentHashMap<Int, PendingPermission>()
    private val stateFlow = MutableStateFlow(DirectUsbControllerState())
    private val inspectionInProgress = AtomicBoolean(false)
    private val sessionGeneration = AtomicLong(0)
    private val sessionLock = Any()
    val state: StateFlow<DirectUsbControllerState> = stateFlow.asStateFlow()

    @Volatile private var activeConnection: UsbDeviceConnection? = null
    @Volatile private var activeSession: UsbDirectSession? = null
    @Volatile private var activeDeviceId: Int? = null
    @Volatile private var activeUacLabel: String? = null
    private var receiverRegistered = false
    private var detachReceiverRegistered = false

    private val permissionAction = "${applicationContext.packageName}.DIRECT_USB_PERMISSION"

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != permissionAction) return
            val device = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            if (device == null) {

                return
            }
            val requestId = intent.getIntExtra(PERMISSION_REQUEST_ID, -1)
            val request = permissionRequests[device.deviceId]
            if (request == null || request.requestId != requestId) {

                return
            }
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            permissionRequests.remove(device.deviceId, request)

            request.result.complete(granted)
        }
    }

    private val detachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != UsbManager.ACTION_USB_DEVICE_DETACHED) return
            val device = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }

            if (device?.deviceId == activeDeviceId) {
                disconnect("device_detached")
                refresh()
                stateFlow.update { it.copy(statusMessage = "USB device detached; native session closed") }
            } else {
                refresh()
            }
        }
    }

    init {
        applicationContext.registerReceiver(
            permissionReceiver,
            IntentFilter(permissionAction),
            Context.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
        applicationContext.registerReceiver(
            detachReceiver,
            IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED),
            Context.RECEIVER_NOT_EXPORTED,
        )
        detachReceiverRegistered = true

    }

    fun refresh(): List<DirectUsbDeviceItem> {
        stateFlow.update { it.copy(isScanning = true, errorMessage = null) }
        val items = usbManager.deviceList.values
            .filter(::isUsbAudioDevice)
            .sortedWith(compareBy<UsbDevice>({ it.vendorId }, { it.productId }, { it.deviceId }))
            .map(::toItem)

        stateFlow.update {
            it.copy(
                devices = items,
                isScanning = false,
                statusMessage = if (items.isEmpty()) {
                    "No USB Audio Class device is currently attached"
                } else {
                    "${items.size} USB Audio device(s) detected"
                },
            )
        }
        return items
    }

    suspend fun inspect(deviceId: Int) {
        val inspectionGeneration = sessionGeneration.get()
        if (!inspectionInProgress.compareAndSet(false, true)) {

            return
        }
        val inspectionAlreadyResolved = synchronized(sessionLock) {
            if (sessionGeneration.get() != inspectionGeneration) {
                true
            } else {
                val retainedSession = activeSession
                if (retainedSession != null && retainedSession.isConnected()) {
                    if (activeDeviceId == deviceId) {

                        stateFlow.update {
                            it.copy(
                                isConnecting = false,
                                selectedDeviceId = deviceId,
                                statusMessage = "The selected USB device already has an active native session",
                                errorMessage = null,
                            )
                        }
                    } else {

                        stateFlow.update {
                            it.copy(
                                isConnecting = false,
                                statusMessage = "Disconnect the current USB device before inspecting another device",
                                errorMessage = "A native USB session is already retained for deviceId=$activeDeviceId",
                            )
                        }
                    }
                    true
                } else {
                    false
                }
            }
        }
        if (inspectionAlreadyResolved) {
            inspectionInProgress.set(false)
            return
        }
        if (synchronized(sessionLock) { activeSession != null || activeConnection != null }) {
            closeActiveSession("stale_session_replaced")
        }
        if (sessionGeneration.get() != inspectionGeneration) {
            inspectionInProgress.set(false)
            return
        }

        var stage = "device_lookup"
        var connection: UsbDeviceConnection? = null
        var session: UsbDirectSession? = null

        fun ensureCurrentInspection() {
            if (sessionGeneration.get() != inspectionGeneration) {
                throw CancellationException("USB inspection was superseded by disconnect")
            }
        }

        fun updateStage(nextStage: String, message: String) {
            ensureCurrentInspection()
            stage = nextStage

            stateFlow.update { it.copy(statusMessage = message) }
        }

        val inspectionStarted = synchronized(sessionLock) {
            if (sessionGeneration.get() != inspectionGeneration) {
                false
            } else {
                stateFlow.update {
                    it.copy(
                        isConnecting = true,
                        selectedDeviceId = deviceId,
                        errorMessage = null,
                        statusMessage = "Locating the selected USB device",
                    )
                }
                true
            }
        }
        if (!inspectionStarted) {
            inspectionInProgress.set(false)
            return
        }


        try {
            val device = usbManager.deviceList.values.firstOrNull { it.deviceId == deviceId }
                ?: throw IllegalStateException("The selected USB device is no longer attached")


            updateStage("permission_request", "Checking USB permission")
            ensurePermission(device)
            ensureCurrentInspection()
            val permissionConfirmed = usbManager.hasPermission(device)

            if (!permissionConfirmed) {
                throw SecurityException("Android did not retain USB permission for the selected device")
            }

            updateStage("android_open_device", "Opening Android USB connection")
            withContext(Dispatchers.IO) {
                connection = usbManager.openDevice(device)
                    ?: throw IllegalStateException("Android could not open the selected USB device")

            }
            ensureCurrentInspection()
            val openedConnection = requireNotNull(connection)
            // The label names the mode the device is operating in: the
            // product name's explicit mode marker when it carries one,
            // otherwise the active configuration read from the live
            // connection, never the first AudioControl protocol in the
            // static descriptor list.
            val sessionUacLabel = withContext(Dispatchers.IO) {
                UsbAudioClass.labelFromProductName(device.productName)
                    ?: UsbAudioClass.labelFromActiveConfiguration(openedConnection, device)
                    ?: UsbAudioClass.bestEffortLabel(device)
            }
            val fileDescriptor = openedConnection.fileDescriptor
            if (fileDescriptor < 0) {
                throw IllegalStateException("Android returned an invalid USB file descriptor")
            }


            updateStage("raw_descriptor_read", "Reading raw USB descriptors")
            val rawDescriptors = withContext(Dispatchers.IO) {
                openedConnection.rawDescriptors
                    ?: throw IllegalStateException("Android did not return raw USB descriptors")
            }
            ensureCurrentInspection()
            if (rawDescriptors.isEmpty()) {
                throw IllegalStateException("Android returned an empty USB descriptor block")
            }

            updateStage("uniffi_constructor", "Calling the Rust USB session constructor")
            withContext(Dispatchers.IO) {
                session = UsbDirectSession.open(
                    fileDescriptor,
                    rawDescriptors,
                )
            }
            ensureCurrentInspection()
            val openedSession = requireNotNull(session)


            updateStage("session_validation", "Validating the native USB session")
            ensureCurrentInspection()
            if (!withContext(Dispatchers.IO) { openedSession.isConnected() }) {
                throw IllegalStateException("The native USB session is not active")
            }

            synchronized(sessionLock) {
                ensureCurrentInspection()
                if (activeSession != null || activeConnection != null) {
                    throw IllegalStateException("Another USB session was retained while this inspection was opening")
                }
                activeConnection = openedConnection
                activeSession = openedSession
                activeDeviceId = deviceId
                activeUacLabel = sessionUacLabel
                connection = null
                session = null
            }

            updateStage("session_retained", "Kotlin, UniFFI, Rust, and libusb are connected")

            stateFlow.update {
                it.copy(
                    isConnecting = false,
                    selectedDeviceId = deviceId,
                    statusMessage = "Kotlin, UniFFI, Rust, and libusb are connected",
                    errorMessage = null,
                )
            }
        } catch (failure: CancellationException) {

            throw failure
        } catch (failure: Throwable) {

            stateFlow.update {
                it.copy(
                    isConnecting = false,
                    selectedDeviceId = null,
                    statusMessage = "USB inspection failed at $stage",
                    errorMessage = failure.message ?: failure::class.java.simpleName,
                )
            }
            if (failure is VirtualMachineError || failure is ThreadDeath) throw failure
        } finally {
            closeLocalResources(session, connection)
            if (sessionGeneration.get() == inspectionGeneration && stateFlow.value.isConnecting) {
                stateFlow.update { it.copy(isConnecting = false) }
            }
            inspectionInProgress.set(false)
        }
    }

    internal fun currentSessionGeneration(): Long = sessionGeneration.get()

    internal fun hasRetainedSession(): Boolean = synchronized(sessionLock) {
        activeSession?.isConnected() == true
    }

    private fun retainedPlaybackSession(): UsbDirectSession {
        return synchronized(sessionLock) { activeSession }
            ?: throw IllegalStateException("Direct USB session is not connected")
    }

    internal suspend fun playbackOpenSource(
        fileDescriptor: Int,
        extensionHint: String,
    ): SourceInfoDto {
        val session = synchronized(sessionLock) { activeSession }
        if (session == null) {
            runCatching { ParcelFileDescriptor.adoptFd(fileDescriptor).close() }.onFailure { }
            throw IllegalStateException("Direct USB session is not connected")
        }
        return withContext(Dispatchers.IO) {
            session.openSource(fileDescriptor, extensionHint)
        }
    }

    internal suspend fun playbackPrepare(): PlaybackStatusDto {
        val session = retainedPlaybackSession()
        return withContext(Dispatchers.IO) { session.preparePlayback() }
    }

    internal suspend fun playbackStart() {
        val session = retainedPlaybackSession()
        withContext(Dispatchers.IO) { session.startPlayback() }
    }

    internal suspend fun playbackPause() {
        val session = retainedPlaybackSession()
        withContext(Dispatchers.IO) { session.pausePlayback() }
    }

    internal suspend fun playbackSeek(positionUs: Long) {
        val session = retainedPlaybackSession()
        withContext(Dispatchers.IO) { session.seekPlayback(positionUs.toULong()) }
    }

    internal suspend fun playbackStop() {
        val session = synchronized(sessionLock) { activeSession } ?: return
        withContext(Dispatchers.IO) { session.stopPlayback() }
    }

    internal suspend fun playbackReleaseSource() {
        val session = synchronized(sessionLock) { activeSession } ?: return
        withContext(Dispatchers.IO) { session.releaseSource() }
    }

    internal fun playbackStatus(): PlaybackStatusDto? {
        val session = synchronized(sessionLock) { activeSession } ?: return null
        return runCatching { session.playbackStatus() }.getOrNull()
    }

    internal fun playbackDacVolume(): DacVolumeDto? {
        val session = synchronized(sessionLock) { activeSession } ?: return null
        return runCatching { session.dacVolume() }.getOrNull()
    }

    internal fun activeDeviceUacVersion(): String? {
        activeUacLabel?.let { return it }
        val deviceId = synchronized(sessionLock) { activeDeviceId } ?: return null
        val device = usbManager.deviceList.values.firstOrNull { it.deviceId == deviceId }
            ?: return null
        return UsbAudioClass.labelForDevice(device)
    }

    internal suspend fun playbackSetDacVolume(db: Double): DacVolumeDto {
        val session = retainedPlaybackSession()
        return withContext(Dispatchers.IO) { session.setDacVolume(db) }
    }

    fun disconnect() {
        disconnect("disconnect_requested")
    }

    private fun disconnect(reason: String) {
        synchronized(sessionLock) {
            sessionGeneration.incrementAndGet()

            closeActiveSessionLocked(reason)
        }
        stateFlow.update {
            it.copy(
                isConnecting = false,
                selectedDeviceId = null,
                statusMessage = "Native USB session closed",
                errorMessage = null,
            )
        }
    }

    fun clearError() {
        stateFlow.update { it.copy(errorMessage = null) }
    }

    override fun close() {

        synchronized(sessionLock) {
            sessionGeneration.incrementAndGet()
            closeActiveSessionLocked("controller_closed")
        }
        permissionRequests.values.forEach { it.result.cancel() }
        permissionRequests.clear()
        if (receiverRegistered) {
            runCatching { applicationContext.unregisterReceiver(permissionReceiver) }.onFailure { }
            receiverRegistered = false
        }
        if (detachReceiverRegistered) {
            runCatching { applicationContext.unregisterReceiver(detachReceiver) }.onFailure { }
            detachReceiverRegistered = false
        }
    }

    private suspend fun ensurePermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {

            return
        }

        val request = PendingPermission(
            requestId = permissionSequence.incrementAndGet(),
            result = CompletableDeferred(),
        )
        permissionRequests.put(device.deviceId, request)?.result?.cancel()
        val intent = Intent(permissionAction)
            .setPackage(applicationContext.packageName)
            .putExtra(PERMISSION_REQUEST_ID, request.requestId)
        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            request.requestId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

        try {

            usbManager.requestPermission(device, pendingIntent)
            val granted = withTimeout(30_000) { request.result.await() }

            if (!granted) throw SecurityException("USB permission was denied")
        } catch (failure: Throwable) {

            throw failure
        } finally {
            permissionRequests.remove(device.deviceId, request)
        }
    }

    private fun closeLocalResources(
        session: UsbDirectSession?,
        connection: UsbDeviceConnection?,
    ) {
        if (session != null) {
            val finalStatus = runCatching { session.shutdown() }
                .onFailure {  }
                .getOrNull()
            if (finalStatus != null) {

            }
            runCatching { session.close() }
                .onSuccess {  }
                .onFailure {  }
        }
        if (connection != null) {
            runCatching { connection.close() }
                .onSuccess {  }
                .onFailure {  }
        }
    }

    private fun closeActiveSession(reason: String) {
        synchronized(sessionLock) {
            closeActiveSessionLocked(reason)
        }
    }

    private fun closeActiveSessionLocked(reason: String) {
        val session = activeSession
        val connection = activeConnection
        val deviceId = activeDeviceId
        activeSession = null
        activeConnection = null
        activeDeviceId = null
        activeUacLabel = null
        if (session != null) {
            val finalStatus = runCatching { session.shutdown() }
                .onFailure {  }
                .getOrNull()
            if (finalStatus != null) {

            } else {
            }
            runCatching { session.close() }
                .onFailure {  }

        }
        if (connection != null) {
            runCatching { connection.close() }
                .onFailure {  }

        }
    }

    private fun isUsbAudioDevice(device: UsbDevice): Boolean {
        if (device.deviceClass == UsbConstants.USB_CLASS_AUDIO) return true
        return (0 until device.interfaceCount).any { index ->
            device.getInterface(index).interfaceClass == UsbConstants.USB_CLASS_AUDIO
        }
    }

    private fun toItem(device: UsbDevice): DirectUsbDeviceItem = DirectUsbDeviceItem(
        deviceId = device.deviceId,
        displayName = device.productName?.takeIf { it.isNotBlank() } ?: "USB Audio device",
        manufacturerName = device.manufacturerName.orEmpty(),
        vendorId = device.vendorId,
        productId = device.productId,
        deviceVersion = device.version.orEmpty(),
        interfaceCount = device.interfaceCount,
        permissionGranted = usbManager.hasPermission(device),
    )

    private companion object {
        const val PERMISSION_REQUEST_ID = "com.rovia.music.extra.DIRECT_USB_PERMISSION_REQUEST_ID"
        val permissionSequence = AtomicInteger()
    }
}

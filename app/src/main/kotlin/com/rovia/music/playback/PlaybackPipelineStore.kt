package com.rovia.music.playback

import com.rovia.music.playback.bitperfect.AudioOutputMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PipelineInfo(
    val mode: AudioOutputMode,
    val offOutputSampleRateHz: Int? = null,
    val offDownsamplerActive: Boolean = false,
    val offDownsampledFromHz: Int? = null,
    val offBufferBytes: Int? = null,
    val offFrameCount: Int? = null,
    val a14SelectedFormat: String? = null,
    val a14DeviceName: String? = null,
    val a14RouteVerified: Boolean = false,
    val usbNegotiatedRateHz: Int? = null,
    val usbNegotiatedChannels: Int? = null,
    val usbNegotiatedBits: Int? = null,
    val usbUacVersion: String? = null,
    val usbVendorId: Int? = null,
    val usbProductId: Int? = null,
    val usbManufacturer: String? = null,
    val usbDeviceName: String? = null,
    val dacVolumeAvailable: Boolean = false,
    val dacVolumeMinDb: Double? = null,
    val dacVolumeMaxDb: Double? = null,
    val dacVolumeCurrentDb: Double? = null,
    val fixedVolumeAllowed: Boolean = false,
    val dapVerifiedRateHz: Int? = null,
    val dapVerifiedChannels: Int? = null,
    val dapVerifiedBits: Int? = null,
)

internal object PlaybackPipelineStore {
    private val _pipeline = MutableStateFlow<PipelineInfo?>(null)
    val pipeline: StateFlow<PipelineInfo?> = _pipeline.asStateFlow()

    fun set(info: PipelineInfo?) {
        _pipeline.value = info
    }

    fun clear() {
        _pipeline.value = null
    }
}

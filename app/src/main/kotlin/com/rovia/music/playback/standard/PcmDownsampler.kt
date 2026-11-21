package com.rovia.music.playback.standard

import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal class PcmDownsampler(
    private val inputRate: Int,
    private val outputRate: Int,
    private val channels: Int,
    private val bytesPerSample: Int,
) {
    init {
        require(inputRate > outputRate) {
            "PcmDownsampler only downsamples (input $inputRate <= output $outputRate)"
        }
        require(outputRate > 0) { "outputRate must be positive" }
        require(channels in 1..2) { "unsupported channel count: $channels" }
        require(bytesPerSample == 2 || bytesPerSample == 3 || bytesPerSample == 4) {
            "unsupported bytesPerSample: $bytesPerSample"
        }
    }

    private val step: Double = inputRate.toDouble() / outputRate

    private val frameBytes: Int = channels * bytesPerSample

    private var position: Double = 0.0

    private var carryFrame: FloatArray? = null

    fun reset() {
        position = 0.0
        carryFrame = null
    }

    fun process(input: ByteArray, eos: Boolean): ByteArray {
        val inSamples = decode(input)
        val inFrameCount = if (channels > 0) inSamples.size / channels else 0
        if (inFrameCount == 0) return ByteArray(0)
        val maxOutFrames = (inFrameCount / step).toInt() + 4
        val outSamples = FloatArray(maxOutFrames * channels)
        var outFrames = 0
        val carry = carryFrame
        while (outFrames < maxOutFrames) {
            val i = floor(position).toInt()
            val frac = (position - i).toFloat()
            if (i + 1 >= inFrameCount) {
                if (!eos || i >= inFrameCount) break
                for (c in 0 until channels) {
                    val a = sampleAt(inSamples, carry, i, c)
                    val b = sampleAt(inSamples, carry, inFrameCount - 1, c)
                    outSamples[outFrames * channels + c] = a * (1f - frac) + b * frac
                }
                outFrames++
                position += step
                continue
            }
            for (c in 0 until channels) {
                val a = sampleAt(inSamples, carry, i, c)
                val b = sampleAt(inSamples, carry, i + 1, c)
                outSamples[outFrames * channels + c] = a * (1f - frac) + b * frac
            }
            outFrames++
            position += step
        }
        carryFrame = FloatArray(channels) { c ->
            inSamples[(inFrameCount - 1) * channels + c]
        }
        position -= inFrameCount
        return encode(outSamples, outFrames)
    }

    private fun sampleAt(frames: FloatArray, carry: FloatArray?, frame: Int, channel: Int): Float {
        return if (frame < 0) {
            carry?.get(channel) ?: 0f
        } else {
            frames[frame * channels + channel]
        }
    }

    private fun decode(input: ByteArray): FloatArray {
        val frameCount = if (frameBytes > 0) input.size / frameBytes else 0
        val out = FloatArray(frameCount * channels)
        var s = 0
        when (bytesPerSample) {
            2 -> {
                for (f in 0 until frameCount) {
                    for (c in 0 until channels) {
                        val o = (f * channels + c) * 2
                        val v = (input[o].toInt() and 0xFF) or (input[o + 1].toInt() shl 8)
                        out[s++] = v.toShort().toFloat() / 32768f
                    }
                }
            }
            3 -> {
                for (f in 0 until frameCount) {
                    for (c in 0 until channels) {
                        val o = (f * channels + c) * 3
                        var v = (input[o].toInt() and 0xFF) or
                            ((input[o + 1].toInt() and 0xFF) shl 8) or
                            ((input[o + 2].toInt() and 0xFF) shl 16)
                        if (v and 0x800000 != 0) v -= 0x1000000
                        out[s++] = v.toFloat() / 8388608f
                    }
                }
            }
            else -> {
                for (f in 0 until frameCount) {
                    for (c in 0 until channels) {
                        val o = (f * channels + c) * 4
                        val v = (input[o].toInt() and 0xFF) or
                            ((input[o + 1].toInt() and 0xFF) shl 8) or
                            ((input[o + 2].toInt() and 0xFF) shl 16) or
                            (input[o + 3].toInt() shl 24)
                        out[s++] = v.toFloat() / 2147483648f
                    }
                }
            }
        }
        return out
    }

    private fun encode(samples: FloatArray, frameCount: Int): ByteArray {
        val out = ByteArray(frameCount * frameBytes)
        var s = 0
        when (bytesPerSample) {
            2 -> {
                for (f in 0 until frameCount) {
                    for (c in 0 until channels) {
                        val v = (samples[s++] * 32767f).roundToInt().coerceIn(-32768, 32767)
                        val o = (f * channels + c) * 2
                        out[o] = (v and 0xFF).toByte()
                        out[o + 1] = ((v ushr 8) and 0xFF).toByte()
                    }
                }
            }
            3 -> {
                for (f in 0 until frameCount) {
                    for (c in 0 until channels) {
                        val v = (samples[s++] * 8388607f).roundToInt().coerceIn(-8388608, 8388607)
                        val o = (f * channels + c) * 3
                        out[o] = (v and 0xFF).toByte()
                        out[o + 1] = ((v ushr 8) and 0xFF).toByte()
                        out[o + 2] = ((v ushr 16) and 0xFF).toByte()
                    }
                }
            }
            else -> {
                for (f in 0 until frameCount) {
                    for (c in 0 until channels) {
                        val v = (samples[s++] * 2147483647.0).roundToLong()
                            .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
                        val o = (f * channels + c) * 4
                        out[o] = (v and 0xFF).toByte()
                        out[o + 1] = ((v ushr 8) and 0xFF).toByte()
                        out[o + 2] = ((v ushr 16) and 0xFF).toByte()
                        out[o + 3] = ((v ushr 24) and 0xFF).toByte()
                    }
                }
            }
        }
        return out
    }
}

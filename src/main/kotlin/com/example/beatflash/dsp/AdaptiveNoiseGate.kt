package com.example.beatflash.dsp

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

data class GateState(
    val inputRms: Double,
    val noiseFloor: Double,
    val threshold: Double,
    val gain: Double
)

class AdaptiveNoiseGate(private val sampleRate: Int) {
    private var noiseFloor = 0.006
    private var gain = 0.0

    fun analyze(samples: FloatArray): GateState {
        if (samples.isEmpty()) return GateState(0.0, noiseFloor, 0.015, gain)
        var squareSum = 0.0
        for (sample in samples) squareSum += sample * sample
        val rms = sqrt(squareSum / samples.size)
        val seconds = samples.size.toDouble() / sampleRate

        if (rms < noiseFloor) {
            val alpha = 1.0 - exp(-seconds / 0.5)
            noiseFloor += (rms - noiseFloor) * alpha
        } else if (rms < noiseFloor * 3.0) {
            val alpha = 1.0 - exp(-seconds / 8.0)
            noiseFloor += (rms - noiseFloor) * alpha
        }

        val threshold = max(0.015, noiseFloor * 2.2)
        val fullyOpen = threshold * 1.6
        var target = ((rms - threshold) / (fullyOpen - threshold)).coerceIn(0.0, 1.0)
        target = target * target * (3.0 - 2.0 * target)
        val response = if (target > gain) 0.025 else 0.18
        val alpha = 1.0 - exp(-seconds / response)
        gain += (target - gain) * alpha
        return GateState(rms, noiseFloor, threshold, gain)
    }
}


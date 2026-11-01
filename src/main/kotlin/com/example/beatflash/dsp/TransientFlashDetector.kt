package com.example.beatflash.dsp

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

data class TransientState(val threshold: Double, val flash: Boolean)

class TransientFlashDetector(private val sampleRate: Int) {
    @Volatile private var thresholdLevel = 0.5
    private var backgroundRms = 0.0
    private var previousRms = 0.0
    private var processedSamples = 0L
    private var lastFlashSample = Long.MIN_VALUE / 2

    fun setThresholdLevel(value: Double) {
        thresholdLevel = value.coerceIn(0.0, 1.0)
    }

    fun process(sampleCount: Int, rms: Double): TransientState {
        val level = thresholdLevel
        val absoluteThreshold = 0.0015 * 400.0.pow(level)
        val relativeThreshold = backgroundRms * (1.05 + 2.95 * level * level)
        val threshold = max(absoluteThreshold, relativeThreshold)
        if (sampleCount <= 0) return TransientState(threshold, false)

        processedSamples += sampleCount
        val minimumGapSamples = sampleRate * 140L / 1_000L
        val flash = rms >= threshold && previousRms < threshold &&
            processedSamples - lastFlashSample >= minimumGapSamples

        val seconds = sampleCount.toDouble() / sampleRate
        val alpha = 1.0 - exp(-seconds / 0.75)
        backgroundRms += (rms - backgroundRms) * alpha
        previousRms = rms
        if (flash) lastFlashSample = processedSamples
        return TransientState(threshold, flash)
    }
}

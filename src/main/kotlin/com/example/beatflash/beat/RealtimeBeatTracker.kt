package com.example.beatflash.beat

import com.example.beatflash.TEMPO_ANALYSIS_INTERVAL_MS
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class BeatState(
    val plpBpm: Double = 0.0,
    val correlationBpm: Double = 0.0,
    val confidence: Double = 0.0,
    val plpConfidence: Double = 0.0,
    val correlationConfidence: Double = 0.0,
    val flash: Boolean = false
)

class RealtimeBeatTracker(
    private val sampleRate: Int,
    private val fftSize: Int,
    private val hopSize: Int
) {
    companion object {
        private const val MIN_BPM = 55
        private const val MAX_BPM = 210
        private const val MIN_FLASH_CONFIDENCE = 0.40
    }

    private val noveltyRate = sampleRate.toDouble() / hopSize
    private val windowSize = (noveltyRate * 4.0).roundToInt().let { if (it % 2 == 0) it + 1 else it }
    private val analysisHop = max(
        1,
        (noveltyRate * TEMPO_ANALYSIS_INTERVAL_MS / 1_000.0).roundToInt()
    )
    private val novelty = DoubleArray(windowSize)
    private var noveltyWrite = 0
    private var noveltyCount = 0
    private val previousSpectrum = DoubleArray(fftSize / 2 + 1)
    private val window = DoubleArray(windowSize) { index ->
        0.5 - 0.5 * cos(2.0 * PI * index / (windowSize - 1))
    }
    private val tempoCount = MAX_BPM - MIN_BPM + 1
    private val kernelReal = Array(tempoCount) { DoubleArray(windowSize) }
    private val kernelImaginary = Array(tempoCount) { DoubleArray(windowSize) }

    private var frameIndex = 0L
    private var framesSinceAnalysis = 0
    private var estimateFrame = 0L
    private var phaseAtEstimate = 0.0
    private var plpBpm = 0.0
    private var correlationBpm = 0.0
    private var plpConfidence = 0.0
    private var correlationConfidence = 0.0
    private var confidence = 0.0
    private var wasInFlashZone = false
    private var lastFlashFrame = Long.MIN_VALUE / 2

    init {
        for (tempoIndex in 0 until tempoCount) {
            val bpm = MIN_BPM + tempoIndex
            val omega = 2.0 * PI * bpm / (60.0 * noveltyRate)
            for (i in 0 until windowSize) {
                kernelReal[tempoIndex][i] = window[i] * cos(omega * i)
                kernelImaginary[tempoIndex][i] = -window[i] * sin(omega * i)
            }
        }
    }

    fun processSpectrum(magnitudes: DoubleArray, gateGain: Double): BeatState {
        val firstBin = max(1, 30 * fftSize / sampleRate)
        val lastBin = minOf(magnitudes.size, 16_000 * fftSize / sampleRate + 1)
        var flux = 0.0
        for (bin in firstBin until lastBin) {
            val current = ln1p(10.0 * magnitudes[bin] * gateGain)
            flux += max(0.0, current - previousSpectrum[bin])
            previousSpectrum[bin] = current
        }

        novelty[noveltyWrite] = flux
        noveltyWrite = (noveltyWrite + 1) % windowSize
        noveltyCount = minOf(windowSize, noveltyCount + 1)
        frameIndex++

        framesSinceAnalysis++
        if (framesSinceAnalysis >= analysisHop) {
            framesSinceAnalysis = 0
            updateEstimate()
        }

        val flash = detectFlash()
        return BeatState(
            plpBpm,
            correlationBpm,
            confidence,
            plpConfidence,
            correlationConfidence,
            flash
        )
    }

    private fun snapshotNovelty(): DoubleArray {
        val result = DoubleArray(noveltyCount)
        val start = (noveltyWrite - noveltyCount + windowSize) % windowSize
        for (i in result.indices) result[i] = novelty[(start + i) % windowSize]
        return result
    }

    private fun updateEstimate() {
        if (noveltyCount < windowSize / 2) return
        val values = snapshotNovelty()
        val offset = windowSize - values.size
        var weightedMean = 0.0
        var windowSum = 0.0
        for (i in values.indices) {
            weightedMean += values[i] * window[offset + i]
            windowSum += window[offset + i]
        }
        weightedMean /= max(1e-12, windowSum)

        val centered = DoubleArray(values.size)
        var localEnergy = 0.0
        for (i in values.indices) {
            centered[i] = max(0.0, values[i] - weightedMean)
            localEnergy += centered[i] * window[offset + i]
        }
        if (localEnergy < 1e-6) {
            plpConfidence *= 0.8
            correlationConfidence *= 0.8
            confidence *= 0.8
            return
        }

        var bestTempoIndex = 0
        var bestMagnitude = -1.0
        var bestReal = 0.0
        var bestImaginary = 0.0
        for (tempoIndex in 0 until tempoCount) {
            var real = 0.0
            var imaginary = 0.0
            for (i in centered.indices) {
                val kernelIndex = offset + i
                real += centered[i] * kernelReal[tempoIndex][kernelIndex]
                imaginary += centered[i] * kernelImaginary[tempoIndex][kernelIndex]
            }
            val magnitude = sqrt(real * real + imaginary * imaginary)
            if (magnitude > bestMagnitude) {
                bestMagnitude = magnitude
                bestTempoIndex = tempoIndex
                bestReal = real
                bestImaginary = imaginary
            }
        }

        val newPlpBpm = (MIN_BPM + bestTempoIndex).toDouble()
        val newPlpConfidence = (bestMagnitude / localEnergy).coerceIn(0.0, 1.0)
        val minimumLag = max(1, (noveltyRate * 60.0 / MAX_BPM).roundToInt())
        val maximumLag = minOf(centered.size - 1, (noveltyRate * 60.0 / MIN_BPM).roundToInt())
        val correlations = DoubleArray(maximumLag - minimumLag + 1)
        for (lag in minimumLag..maximumLag) {
            var product = 0.0
            var firstEnergy = 0.0
            var secondEnergy = 0.0
            for (i in lag until centered.size) {
                val first = centered[i]
                val second = centered[i - lag]
                product += first * second
                firstEnergy += first * first
                secondEnergy += second * second
            }
            val denominator = sqrt(firstEnergy * secondEnergy)
            correlations[lag - minimumLag] = if (denominator > 1e-12) {
                (product / denominator).coerceIn(0.0, 1.0)
            } else 0.0
        }

        var strongestIndex = 0
        for (i in 1 until correlations.size) {
            if (correlations[i] > correlations[strongestIndex]) strongestIndex = i
        }
        val strongestLag = minimumLag + strongestIndex
        val newCorrelationBpm = 60.0 * noveltyRate / strongestLag

        val selectedLag = (noveltyRate * 60.0 / newPlpBpm).roundToInt()
            .coerceIn(minimumLag, maximumLag)
        var selectedCorrelation = 0.0
        for (lag in max(minimumLag, selectedLag - 1)..minOf(maximumLag, selectedLag + 1)) {
            selectedCorrelation = max(selectedCorrelation, correlations[lag - minimumLag])
        }
        val sorted = correlations.copyOf().apply { sort() }
        val median = sorted[sorted.size / 2]
        val prominence = ((selectedCorrelation - median) / max(1e-12, 1.0 - median))
            .coerceIn(0.0, 1.0)
        val newCorrelationConfidence = 0.5 * selectedCorrelation + 0.5 * prominence

        val omega = 2.0 * PI * newPlpBpm / (60.0 * noveltyRate)
        plpBpm = newPlpBpm
        correlationBpm = newCorrelationBpm
        phaseAtEstimate = Math.IEEEremainder(
            omega * (windowSize - 1) + atan2(bestImaginary, bestReal),
            2.0 * PI
        )
        estimateFrame = frameIndex
        plpConfidence = newPlpConfidence
        correlationConfidence = newCorrelationConfidence
        confidence = sqrt(plpConfidence * correlationConfidence)
    }

    private fun detectFlash(): Boolean {
        if (plpBpm <= 0.0) return false
        val omega = 2.0 * PI * plpBpm / (60.0 * noveltyRate)
        val phase = phaseAtEstimate + omega * (frameIndex - estimateFrame)
        val inFlashZone = cos(phase) > 0.92
        val minimumGap = (noveltyRate * 60.0 / plpBpm * 0.55).roundToInt()
        val trigger = confidence >= MIN_FLASH_CONFIDENCE &&
            inFlashZone && !wasInFlashZone && frameIndex - lastFlashFrame >= minimumGap
        wasInFlashZone = inFlashZone
        if (trigger) lastFlashFrame = frameIndex
        return trigger
    }
}


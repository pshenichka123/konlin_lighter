package com.example.beatflash.dsp

import java.util.ArrayDeque
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

class SpectrumNormalizer(
    private val sampleRate: Int,
    private val fftSize: Int,
    private val barCount: Int = 64
) {
    private data class Peak(val timeNanos: Long, val value: Double)
    private val peaks = ArrayDeque<Peak>()
    private val displayed = FloatArray(barCount)
    private val binStarts = IntArray(barCount)
    private val binEnds = IntArray(barCount)

    init {
        val minimum = 20.0
        val maximum = minOf(20_000.0, sampleRate / 2.0)
        val step = (ln(maximum) - ln(minimum)) / barCount
        val binWidth = sampleRate.toDouble() / fftSize
        val lastBin = fftSize / 2
        for (bar in 0 until barCount) {
            val low = exp(ln(minimum) + bar * step)
            val high = exp(ln(minimum) + (bar + 1) * step)
            binStarts[bar] = (low / binWidth).toInt().coerceIn(0, lastBin)
            binEnds[bar] = max(binStarts[bar] + 1, (high / binWidth).toInt())
                .coerceAtMost(lastBin + 1)
        }
    }

    fun process(magnitudes: DoubleArray): FloatArray {
        val targets = DoubleArray(barCount)
        var frameMaximum = 0.0
        for (bar in 0 until barCount) {
            var maximum = 0.0
            for (bin in binStarts[bar] until binEnds[bar]) maximum = max(maximum, magnitudes[bin])
            targets[bar] = maximum
            frameMaximum = max(frameMaximum, maximum)
        }

        val now = System.nanoTime()
        val cutoff = now - 30_000_000_000L
        while (peaks.isNotEmpty() && peaks.first.timeNanos < cutoff) peaks.removeFirst()
        while (peaks.isNotEmpty() && peaks.last.value <= frameMaximum) peaks.removeLast()
        peaks.addLast(Peak(now, frameMaximum))
        val rollingMaximum = peaks.first.value
        for (bar in 0 until barCount) {
            val target = if (rollingMaximum > 0.0) (targets[bar] / rollingMaximum).toFloat() else 0f
            displayed[bar] += (target - displayed[bar]) * 0.28f
        }
        return displayed.copyOf()
    }
}


package com.example.beatflash.beat

import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Finds a repeating onset interval without quantizing it to a number of beats. */
internal class PatternFlashTracker(private val noveltyRate: Double) {
    companion object {
        private const val HISTORY_SECONDS = 30
        private const val MIN_PERIOD_SECONDS = 0.25
        private const val MAX_PERIOD_SECONDS = 12.0
        private const val MIN_COMPARISON_SECONDS = 6
        private const val UPDATE_SECONDS = 0.25
        private const val MIN_SIMILARITY = 0.60
    }

    private data class Pattern(
        val periodFrames: Int,
        val similarity: Double,
        val peaks: BooleanArray,
        val anchorFrame: Long
    )

    private val history = DoubleArray((noveltyRate * HISTORY_SECONDS).toInt() + 1)
    private var writeIndex = 0
    private var historyCount = 0
    private var frameIndex = 0L
    private var framesUntilUpdate = 0
    private var pattern: Pattern? = null
    private var wasInPeak = false
    private var framesSinceFlash = Int.MAX_VALUE / 2
    private var previousNovelty = 0.0
    private var noveltyBeforePrevious = 0.0

    val periodSeconds: Double get() = (pattern?.periodFrames ?: 0) / noveltyRate
    val patternSimilarity: Double get() = pattern?.similarity ?: 0.0

    fun process(novelty: Double): Boolean {
        val compressed = ln1p(novelty)
        val smoothed = 0.5 * compressed + 0.3 * previousNovelty + 0.2 * noveltyBeforePrevious
        noveltyBeforePrevious = previousNovelty
        previousNovelty = compressed
        history[writeIndex] = smoothed
        writeIndex = (writeIndex + 1) % history.size
        historyCount = min(historyCount + 1, history.size)
        frameIndex++

        if (framesUntilUpdate <= 0) {
            pattern = findPattern()
            framesUntilUpdate = max(1, (noveltyRate * UPDATE_SECONDS).toInt())
        }
        framesUntilUpdate--

        val selected = pattern
        if (selected == null) {
            wasInPeak = false
            return false
        }

        val position = ((selected.periodFrames - 1L + frameIndex - selected.anchorFrame) %
            selected.periodFrames).toInt()
        val inPeak = selected.peaks[position]
        framesSinceFlash = min(framesSinceFlash + 1, Int.MAX_VALUE / 2)
        val minimumGap = max(1, (noveltyRate * 0.08).toInt())
        val flash = inPeak && !wasInPeak && framesSinceFlash >= minimumGap
        wasInPeak = inPeak
        if (flash) framesSinceFlash = 0
        return flash
    }

    private fun findPattern(): Pattern? {
        val minLag = max(1, (noveltyRate * MIN_PERIOD_SECONDS).toInt())
        val minimumComparison = (noveltyRate * MIN_COMPARISON_SECONDS).toInt()
        val maxLag = minOf(
            (noveltyRate * MAX_PERIOD_SECONDS).toInt(),
            historyCount / 2,
            historyCount - minimumComparison
        )
        if (maxLag < minLag) return null

        val recent = DoubleArray(maxLag + max(maxLag, minimumComparison)) { age ->
            history[(writeIndex - 1 - age + history.size) % history.size]
        }
        var bestLag = 0
        var bestSimilarity = 0.0
        var bestAdjusted = Double.NEGATIVE_INFINITY
        for (lag in minLag..maxLag) {
            val comparisonFrames = max(lag, minimumComparison)
            var dot = 0.0
            var firstEnergy = 0.0
            var secondEnergy = 0.0
            for (age in 0 until comparisonFrames) {
                val first = recent[age]
                val second = recent[age + lag]
                dot += first * second
                firstEnergy += first * first
                secondEnergy += second * second
            }
            val energy = sqrt(firstEnergy * secondEnergy)
            if (energy < 1e-4) continue
            val similarity = (dot / energy).coerceIn(0.0, 1.0)
            if (similarity < MIN_SIMILARITY) continue
            val adjusted = similarity - 0.02 * ln(lag.toDouble() / minLag)
            if (adjusted > bestAdjusted) {
                bestLag = lag
                bestSimilarity = similarity
                bestAdjusted = adjusted
            }
        }
        if (bestLag == 0) return null

        val firstMaximum = (0 until bestLag).maxOf { recent[it] }
        val secondMaximum = (bestLag until 2 * bestLag).maxOf { recent[it] }
        if (min(firstMaximum, secondMaximum) < 0.05) return null
        val shared = DoubleArray(bestLag) { position ->
            val age = bestLag - 1 - position
            min(recent[age] / firstMaximum, recent[age + bestLag] / secondMaximum)
        }
        val candidates = (0 until bestLag)
            .filter { position ->
                val left = shared[(position - 1 + bestLag) % bestLag]
                val right = shared[(position + 1) % bestLag]
                shared[position] >= 0.35 && shared[position] >= left && shared[position] > right
            }
            .sortedByDescending { shared[it] }
        val peaks = BooleanArray(bestLag)
        val separation = max(1, (noveltyRate * 0.08).toInt())
        for (position in candidates) {
            if ((-separation..separation).none { offset ->
                    peaks[(position + offset + bestLag) % bestLag]
                }) {
                peaks[position] = true
            }
        }
        if (peaks.none { it }) return null
        return Pattern(bestLag, bestSimilarity, peaks, frameIndex)
    }
}

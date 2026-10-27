package com.example.beatflash

import com.example.beatflash.audio.MicrophoneSource
import com.example.beatflash.beat.BeatState
import com.example.beatflash.beat.RealtimeBeatTracker
import com.example.beatflash.dsp.AdaptiveNoiseGate
import com.example.beatflash.dsp.Fft
import com.example.beatflash.dsp.GateState
import com.example.beatflash.dsp.SpectrumNormalizer
import kotlin.math.PI
import kotlin.math.cos

data class LiveState(
    val beat: BeatState,
    val gate: GateState
)

class BeatEngine(private val listener: Listener) : AutoCloseable {
    interface Listener {
        fun onState(state: LiveState)
        fun onSpectrum(values: FloatArray)
        fun onFlash()
        fun onError(message: String)
    }

    companion object {
        private const val SAMPLE_RATE = 48_000
        private const val FFT_SIZE = 2048
        private const val HOP_SIZE = 480
    }

    private val fft = Fft(FFT_SIZE)
    private val fftWindow = DoubleArray(FFT_SIZE) { index ->
        0.5 - 0.5 * cos(2.0 * PI * index / (FFT_SIZE - 1))
    }
    private val magnitudes = DoubleArray(FFT_SIZE / 2 + 1)
    private val frame = FloatArray(FFT_SIZE)
    private val tracker = RealtimeBeatTracker(SAMPLE_RATE, FFT_SIZE, HOP_SIZE)
    private val gate = AdaptiveNoiseGate(SAMPLE_RATE)
    private val spectrum = SpectrumNormalizer(SAMPLE_RATE, FFT_SIZE)
    private val microphone = MicrophoneSource(SAMPLE_RATE, ::onSamples, listener::onError)
    private var pending = FloatArray(FFT_SIZE * 4)
    private var pendingSize = 0
    private var gateState = GateState(0.0, 0.006, 0.015, 0.0)
    private var processedFrames = 0L

    fun start(): Boolean = microphone.start()
    fun isRunning(): Boolean = microphone.isRunning()

    private fun onSamples(samples: FloatArray) {
        gateState = gate.analyze(samples)
        ensureCapacity(pendingSize + samples.size)
        samples.copyInto(pending, pendingSize)
        pendingSize += samples.size

        while (pendingSize >= FFT_SIZE) {
            pending.copyInto(frame, endIndex = FFT_SIZE)
            fft.magnitudes(frame, fftWindow, magnitudes)
            val beatState = tracker.processSpectrum(magnitudes, gateState.gain)
            processedFrames++

            if (processedFrames % 3L == 0L) listener.onSpectrum(spectrum.process(magnitudes))
            if (processedFrames % 10L == 0L || beatState.flash) {
                listener.onState(LiveState(beatState, gateState))
            }
            if (beatState.flash) listener.onFlash()

            System.arraycopy(pending, HOP_SIZE, pending, 0, pendingSize - HOP_SIZE)
            pendingSize -= HOP_SIZE
        }
    }

    private fun ensureCapacity(required: Int) {
        if (required <= pending.size) return
        var size = pending.size
        while (size < required) size *= 2
        pending = pending.copyOf(size)
    }

    override fun close() {
        microphone.close()
    }
}


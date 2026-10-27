package com.example.beatflash.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class Fft(private val size: Int) {
    private val real = DoubleArray(size)
    private val imaginary = DoubleArray(size)

    init {
        require(size > 1 && size and (size - 1) == 0) { "FFT size must be a power of two" }
    }

    fun magnitudes(input: FloatArray, window: DoubleArray, output: DoubleArray) {
        require(input.size >= size && window.size == size && output.size >= size / 2 + 1)
        for (i in 0 until size) {
            real[i] = input[i] * window[i]
            imaginary[i] = 0.0
        }

        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val realValue = real[i]
                real[i] = real[j]
                real[j] = realValue
                val imaginaryValue = imaginary[i]
                imaginary[i] = imaginary[j]
                imaginary[j] = imaginaryValue
            }
        }

        var length = 2
        while (length <= size) {
            val angle = -2.0 * PI / length
            val rootReal = cos(angle)
            val rootImaginary = sin(angle)
            val half = length / 2
            var start = 0
            while (start < size) {
                var rotationReal = 1.0
                var rotationImaginary = 0.0
                for (offset in 0 until half) {
                    val even = start + offset
                    val odd = even + half
                    val oddReal = real[odd] * rotationReal - imaginary[odd] * rotationImaginary
                    val oddImaginary = real[odd] * rotationImaginary + imaginary[odd] * rotationReal
                    real[odd] = real[even] - oddReal
                    imaginary[odd] = imaginary[even] - oddImaginary
                    real[even] += oddReal
                    imaginary[even] += oddImaginary
                    val nextReal = rotationReal * rootReal - rotationImaginary * rootImaginary
                    rotationImaginary = rotationReal * rootImaginary + rotationImaginary * rootReal
                    rotationReal = nextReal
                }
                start += length
            }
            length = length shl 1
        }

        for (i in output.indices) output[i] = hypot(real[i], imaginary[i])
    }
}


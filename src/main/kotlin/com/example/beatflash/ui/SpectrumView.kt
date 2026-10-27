package com.example.beatflash.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.max

class SpectrumView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var values = FloatArray(64)

    init {
        setBackgroundColor(Color.rgb(12, 14, 24))
    }

    fun updateSpectrum(newValues: FloatArray) {
        values = newValues.copyOf()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return
        val barWidth = width.toFloat() / values.size
        val gap = max(1f, barWidth * 0.12f)
        values.forEachIndexed { index, value ->
            val fraction = index.toFloat() / values.size
            paint.color = Color.HSVToColor(floatArrayOf(210f - 180f * fraction, 0.75f, 1f))
            val left = index * barWidth + gap / 2f
            val right = (index + 1) * barWidth - gap / 2f
            val top = height * (1f - value.coerceIn(0f, 1f) * 0.94f)
            canvas.drawRect(left, top, right, height.toFloat(), paint)
        }
    }
}


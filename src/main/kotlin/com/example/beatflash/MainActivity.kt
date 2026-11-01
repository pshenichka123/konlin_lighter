package com.example.beatflash

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.example.beatflash.flash.TorchController
import com.example.beatflash.ui.SpectrumView
import java.util.Locale

class MainActivity : Activity(), BeatEngine.Listener {
    companion object {
        private const val PERMISSION_REQUEST = 100
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
    }

    private lateinit var status: TextView
    private lateinit var details: TextView
    private lateinit var startButton: Button
    private lateinit var standardButton: Button
    private lateinit var simpleButton: Button
    private lateinit var thresholdLabel: TextView
    private lateinit var thresholdSlider: SeekBar
    private lateinit var spectrum: SpectrumView
    private var mode = ProcessingMode.STANDARD
    private var engine: BeatEngine? = null
    private var torch: TorchController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildInterface()
        startButton.setOnClickListener {
            if (engine?.isRunning() == true) stopListening() else requestAndStart()
        }
        standardButton.setOnClickListener { selectMode(ProcessingMode.STANDARD) }
        simpleButton.setOnClickListener { selectMode(ProcessingMode.SIMPLE) }
        thresholdSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val level = progress / 100.0
                thresholdLabel.text = String.format(
                    Locale.US,
                    "Порог вспышки: %.2f\n0 — любой писк, 1 — почти никогда",
                    level
                )
                engine?.setSimpleThreshold(level)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
        requestAndStart()
    }

    private fun buildInterface() {
        val density = resources.displayMetrics.density
        val padding = (18 * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.rgb(10, 12, 20))
        }

        status = TextView(this).apply {
            text = "Ожидание разрешений…"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        details = TextView(this).apply {
            text = "Стандартный обработчик оценивает темп после накопления звука."
            textSize = 15f
            setTextColor(Color.rgb(185, 200, 220))
            gravity = Gravity.CENTER
            setPadding(0, padding / 2, 0, padding)
        }
        spectrum = SpectrumView(this)
        standardButton = Button(this).apply { text = "стандартный обработчик" }
        simpleButton = Button(this).apply { text = "тупой обработчик" }
        thresholdLabel = TextView(this).apply {
            text = "Порог вспышки: 0.50\n0 — любой писк, 1 — почти никогда"
            textSize = 14f
            setTextColor(Color.rgb(185, 200, 220))
            gravity = Gravity.CENTER
        }
        thresholdSlider = SeekBar(this).apply {
            max = 100
            progress = 50
            contentDescription = "Порог вспышки тупого обработчика"
        }
        startButton = Button(this).apply { text = "Начать слушать" }
        updateModeButtons()

        root.addView(status, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(details, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(spectrum, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        root.addView(standardButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(simpleButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(thresholdLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(thresholdSlider, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        root.addView(startButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        setContentView(root)
    }

    private fun updateModeButtons() {
        standardButton.isEnabled = mode != ProcessingMode.STANDARD
        simpleButton.isEnabled = mode != ProcessingMode.SIMPLE
        thresholdLabel.visibility = if (mode == ProcessingMode.SIMPLE) View.VISIBLE else View.GONE
        thresholdSlider.visibility = if (mode == ProcessingMode.SIMPLE) View.VISIBLE else View.GONE
    }

    private fun selectMode(selected: ProcessingMode) {
        if (mode == selected && engine?.isRunning() == true) return
        mode = selected
        updateModeButtons()
        requestAndStart()
    }

    private fun hasPermissions(): Boolean = REQUIRED_PERMISSIONS.all {
        checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAndStart() {
        if (hasPermissions()) {
            startListening()
        } else {
            requestPermissions(REQUIRED_PERMISSIONS, PERMISSION_REQUEST)
        }
    }

    private fun startListening() {
        stopListening()
        torch = TorchController(this)
        engine = BeatEngine(this, mode)
        engine?.setSimpleThreshold(thresholdSlider.progress / 100.0)
        if (engine?.start() == true) {
            startButton.text = "Остановить"
            status.text = if (mode == ProcessingMode.SIMPLE) "Тупой обработчик" else "Слушаю микрофон"
            details.text = when {
                torch?.isAvailable != true -> "Фонарик не найден, анализ звука продолжается."
                mode == ProcessingMode.SIMPLE -> "Вспышка при резком усилении звука, без оценки BPM."
                else -> "Фонарик готов. Для первой оценки темпа нужно около 2 секунд."
            }
        } else {
            engine?.close()
            engine = null
            startButton.text = "Повторить"
        }
    }

    private fun stopListening() {
        engine?.close()
        engine = null
        torch?.close()
        torch = null
        if (::startButton.isInitialized) startButton.text = "Начать слушать"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_REQUEST) return
        if (hasPermissions()) {
            startListening()
        } else {
            status.text = "Нет доступа к микрофону или фонарику"
            details.text = "Разрешите микрофон и камеру в настройках Android."
        }
    }

    override fun onState(state: LiveState) {
        runOnUiThread {
            val beat = state.beat
            if (beat == null) {
                if (mode != ProcessingMode.SIMPLE) return@runOnUiThread
                status.text = "Тупой обработчик"
                details.text = String.format(
                    Locale.US,
                    "RMS %.3f  порог вспышки %.3f",
                    state.gate.inputRms,
                    state.transientThreshold
                )
            } else {
                if (mode != ProcessingMode.STANDARD) return@runOnUiThread
                status.text = String.format(
                    Locale.US,
                    "PLP %.0f / Corr %.0f BPM",
                    beat.plpBpm,
                    beat.correlationBpm
                )
                details.text = String.format(
                    Locale.US,
                    "confidence %.2f  (PLP %.2f, corr %.2f)\n" +
                        "RMS %.3f  gate %.2f  threshold %.3f",
                    beat.confidence,
                    beat.plpConfidence,
                    beat.correlationConfidence,
                    state.gate.inputRms,
                    state.gate.gain,
                    state.gate.threshold
                )
            }
        }
    }

    override fun onSpectrum(values: FloatArray) {
        runOnUiThread { spectrum.updateSpectrum(values) }
    }

    override fun onFlash() {
        runOnUiThread { torch?.pulse() }
    }

    override fun onError(message: String) {
        runOnUiThread {
            status.text = "Ошибка микрофона"
            details.text = message
        }
    }

    override fun onStop() {
        stopListening()
        super.onStop()
    }

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }
}

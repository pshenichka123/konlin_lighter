package com.example.beatflash

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
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
    private lateinit var spectrum: SpectrumView
    private var engine: BeatEngine? = null
    private var torch: TorchController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildInterface()
        startButton.setOnClickListener {
            if (engine?.isRunning() == true) stopListening() else requestAndStart()
        }
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
            text = "РћР¶РёРґР°РЅРёРµ СЂР°Р·СЂРµС€РµРЅРёР№вЂ¦"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        details = TextView(this).apply {
            text = "PLP Рё РєРѕСЂСЂРµР»СЏС†РёСЏ РЅР°С‡РЅСѓС‚ СЂР°Р±РѕС‚Р°С‚СЊ РїРѕСЃР»Рµ РЅР°РєРѕРїР»РµРЅРёСЏ Р·РІСѓРєР°"
            textSize = 15f
            setTextColor(Color.rgb(185, 200, 220))
            gravity = Gravity.CENTER
            setPadding(0, padding / 2, 0, padding)
        }
        spectrum = SpectrumView(this)
        startButton = Button(this).apply { text = "РќР°С‡Р°С‚СЊ СЃР»СѓС€Р°С‚СЊ" }

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
        root.addView(startButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        setContentView(root)
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
        engine = BeatEngine(this)
        if (engine?.start() == true) {
            startButton.text = "РћСЃС‚Р°РЅРѕРІРёС‚СЊ"
            status.text = "РЎР»СѓС€Р°СЋ РјРёРєСЂРѕС„РѕРЅ"
            details.text = if (torch?.isAvailable == true) {
                "Р¤РѕРЅР°СЂРёРє РіРѕС‚РѕРІ. Р”Р»СЏ РїРµСЂРІРѕР№ РѕС†РµРЅРєРё РЅСѓР¶РЅРѕ РѕРєРѕР»Рѕ 2 СЃРµРєСѓРЅРґ."
            } else {
                "Р¤РѕРЅР°СЂРёРє РЅРµ РЅР°Р№РґРµРЅ, Р°РЅР°Р»РёР· Р·РІСѓРєР° РїСЂРѕРґРѕР»Р¶Р°РµС‚ СЂР°Р±РѕС‚Р°С‚СЊ."
            }
        } else {
            engine?.close()
            engine = null
            startButton.text = "РџРѕРІС‚РѕСЂРёС‚СЊ"
        }
    }

    private fun stopListening() {
        engine?.close()
        engine = null
        torch?.close()
        torch = null
        if (::startButton.isInitialized) startButton.text = "РќР°С‡Р°С‚СЊ СЃР»СѓС€Р°С‚СЊ"
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
            status.text = "РќРµС‚ РґРѕСЃС‚СѓРїР° Рє РјРёРєСЂРѕС„РѕРЅСѓ РёР»Рё С„РѕРЅР°СЂРёРєСѓ"
            details.text = "Р Р°Р·СЂРµС€РёС‚Рµ РјРёРєСЂРѕС„РѕРЅ Рё РєР°РјРµСЂСѓ РІ РЅР°СЃС‚СЂРѕР№РєР°С… Android."
        }
    }

    override fun onState(state: LiveState) {
        runOnUiThread {
            val beat = state.beat
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

    override fun onSpectrum(values: FloatArray) {
        runOnUiThread { spectrum.updateSpectrum(values) }
    }

    override fun onFlash() {
        runOnUiThread { torch?.pulse() }
    }

    override fun onError(message: String) {
        runOnUiThread {
            status.text = "РћС€РёР±РєР° РјРёРєСЂРѕС„РѕРЅР°"
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


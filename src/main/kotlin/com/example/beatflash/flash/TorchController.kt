package com.example.beatflash.flash

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

class TorchController(context: Context) : AutoCloseable {
    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val cameraId: String? = findTorchCamera()
    private val turnOff = Runnable { setTorch(false) }

    val isAvailable: Boolean get() = cameraId != null

    private fun findTorchCamera(): String? {
        var fallback: String? = null
        return try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                if (characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) != true) continue
                if (fallback == null) fallback = id
                if (characteristics.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK) return id
            }
            fallback
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun setTorch(enabled: Boolean) {
        val id = cameraId ?: return
        try {
            cameraManager.setTorchMode(id, enabled)
        } catch (_: Exception) {
            // The camera may temporarily be busy in another application.
        }
    }

    fun pulse(durationMillis: Long = 70L) {
        if (cameraId == null) return
        handler.removeCallbacks(turnOff)
        setTorch(true)
        handler.postDelayed(turnOff, durationMillis)
    }

    override fun close() {
        handler.removeCallbacks(turnOff)
        setTorch(false)
    }
}


package com.example.beatflash.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.max

class MicrophoneSource(
    val sampleRate: Int = 48_000,
    private val onSamples: (FloatArray) -> Unit,
    private val onError: (String) -> Unit
) : AutoCloseable {
    private val running = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var captureThread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running.get()) return true
        val minimumBytes = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minimumBytes <= 0) {
            onError("РњРёРєСЂРѕС„РѕРЅ РЅРµ РїРѕРґРґРµСЂР¶РёРІР°РµС‚ С‡Р°СЃС‚РѕС‚Сѓ $sampleRate Р“С†")
            return false
        }

        val bufferBytes = max(minimumBytes * 2, sampleRate / 5 * 2)
        val created = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferBytes)
                .build()
        } catch (exception: Exception) {
            onError("РќРµ СѓРґР°Р»РѕСЃСЊ РѕС‚РєСЂС‹С‚СЊ РјРёРєСЂРѕС„РѕРЅ: ${exception.message}")
            return false
        }
        if (created.state != AudioRecord.STATE_INITIALIZED) {
            created.release()
            onError("AudioRecord РЅРµ РёРЅРёС†РёР°Р»РёР·РёСЂРѕРІР°РЅ")
            return false
        }

        recorder = created
        running.set(true)
        try {
            created.startRecording()
        } catch (exception: Exception) {
            running.set(false)
            created.release()
            recorder = null
            onError("РќРµ СѓРґР°Р»РѕСЃСЊ РЅР°С‡Р°С‚СЊ Р·Р°РїРёСЃСЊ: ${exception.message}")
            return false
        }

        captureThread = thread(name = "BeatFlash-Microphone", isDaemon = true) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val shortBuffer = ShortArray(2048)
            while (running.get()) {
                val count = created.read(shortBuffer, 0, shortBuffer.size, AudioRecord.READ_BLOCKING)
                if (count > 0) {
                    val samples = FloatArray(count)
                    for (i in 0 until count) samples[i] = shortBuffer[i] / 32768f
                    onSamples(samples)
                } else if (count < 0 && running.get()) {
                    onError("РћС€РёР±РєР° С‡С‚РµРЅРёСЏ РјРёРєСЂРѕС„РѕРЅР°: $count")
                    break
                }
            }
            running.set(false)
        }
        return true
    }

    fun isRunning(): Boolean = running.get()

    override fun close() {
        running.set(false)
        recorder?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
            }
        }
        captureThread?.join(500)
        captureThread = null
        recorder?.release()
        recorder = null
    }
}


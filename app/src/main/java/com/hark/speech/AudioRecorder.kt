package com.hark.speech

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Records mic audio to an m4a file for Whisper transcription. [maxAmplitude] drives the
 * waveform while recording. Not thread-safe; drive it from one place (the Talk ViewModel).
 */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start() {
        val file = File(context.cacheDir, "hark_talk_${System.currentTimeMillis()}.m4a")
        outputFile = file
        recorder = buildRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(96_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
    }

    /** 0..32767; 0 when not recording. */
    fun maxAmplitude(): Int = try {
        recorder?.maxAmplitude ?: 0
    } catch (e: Exception) {
        0
    }

    /** Stops and returns the recorded file (or null on failure). */
    fun stop(): File? {
        return try {
            recorder?.stop()
            outputFile
        } catch (e: Exception) {
            outputFile?.delete()
            null
        } finally {
            recorder?.release()
            recorder = null
        }
    }

    fun cancel() {
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
        outputFile?.delete()
        outputFile = null
    }

    @Suppress("DEPRECATION")
    private fun buildRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
}

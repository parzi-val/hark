package com.hark.ui.talk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hark.ai.OpenAiClient
import com.hark.ai.TidyService
import com.hark.data.local.Source
import com.hark.data.repo.HarkRepository
import com.hark.domain.TidyResult
import com.hark.speech.AudioRecorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class TalkPhase { IDLE, LISTENING, TRANSCRIBING, TIDYING, RESULT, ERROR }

data class TalkUiState(
    val phase: TalkPhase = TalkPhase.IDLE,
    val transcript: String = "",
    val level: Float = 0f,
    val elapsed: Int = 0,
    val result: TidyResult? = null,
    val error: String? = null,
)

/**
 * Talk flow: record mic audio → Whisper transcription → LLM tidy → keep.
 * On-device recognition was too lossy, so speech goes through Groq Whisper (same key).
 */
class TalkViewModel(
    private val repo: HarkRepository,
    private val tidy: TidyService,
    private val client: OpenAiClient,
    private val recorderFactory: () -> AudioRecorder,
) : ViewModel() {

    private val _ui = MutableStateFlow(TalkUiState())
    val ui: StateFlow<TalkUiState> = _ui.asStateFlow()

    private var recorder: AudioRecorder? = null
    private var meterJob: Job? = null
    private var transcript = ""

    /** Start recording. Call only after RECORD_AUDIO is granted. */
    fun start() {
        stopMeter()
        transcript = ""
        try {
            recorder = recorderFactory().also { it.start() }
        } catch (e: Exception) {
            _ui.value = TalkUiState(phase = TalkPhase.ERROR, error = "Couldn't start the microphone.")
            return
        }
        _ui.value = TalkUiState(phase = TalkPhase.LISTENING)
        val startedAt = System.currentTimeMillis()
        meterJob = viewModelScope.launch {
            while (isActive) {
                val amp = recorder?.maxAmplitude() ?: 0
                val secs = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
                _ui.update { it.copy(level = (amp / 20_000f).coerceIn(0f, 1f), elapsed = secs) }
                delay(80)
            }
        }
    }

    fun stopAndTidy() {
        stopMeter()
        val file = recorder?.stop()
        recorder = null
        if (file == null) {
            _ui.update { it.copy(phase = TalkPhase.ERROR, error = "Nothing recorded. Try again.") }
            return
        }
        _ui.update { it.copy(phase = TalkPhase.TRANSCRIBING) }
        viewModelScope.launch {
            val text = try {
                client.transcribe(file)
            } catch (e: Exception) {
                _ui.update { it.copy(phase = TalkPhase.ERROR, error = e.message ?: "Transcription failed.") }
                return@launch
            } finally {
                file.delete()
            }
            if (text.isBlank()) {
                _ui.update { it.copy(phase = TalkPhase.ERROR, error = "I didn't catch anything. Try again.") }
                return@launch
            }
            transcript = text
            _ui.update { it.copy(transcript = text, phase = TalkPhase.TIDYING) }
            val result = tidy.tidy(text, LocalDate.now())
            _ui.update { it.copy(phase = TalkPhase.RESULT, result = result) }
        }
    }

    fun again() = start()

    /** Surface a terminal error (e.g. microphone permission denied). */
    fun fail(message: String) {
        stopMeter()
        recorder?.cancel()
        recorder = null
        _ui.update { it.copy(phase = TalkPhase.ERROR, error = message) }
    }

    fun keep(onDone: () -> Unit) {
        val result = _ui.value.result ?: return
        val heard = transcript
        viewModelScope.launch {
            repo.saveTidied(result, heardAs = heard.ifBlank { null }, source = Source.SPOKEN)
            onDone()
        }
    }

    private fun stopMeter() {
        meterJob?.cancel()
        meterJob = null
    }

    override fun onCleared() {
        stopMeter()
        recorder?.cancel()
    }
}

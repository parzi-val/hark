package com.hark.ui.talk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hark.ai.HarkService
import com.hark.ai.OpenAiClient
import com.hark.data.local.Source
import com.hark.data.repo.HarkRepository
import com.hark.domain.Action
import com.hark.domain.FocusedNote
import com.hark.domain.HarkAction
import com.hark.speech.AudioRecorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class TalkPhase { IDLE, LISTENING, TRANSCRIBING, TIDYING, RESULT, ERROR }

data class TalkUiState(
    val phase: TalkPhase = TalkPhase.IDLE,
    val transcript: String = "",
    val level: Float = 0f,
    val elapsed: Int = 0,
    val extractTasks: Boolean = true,
    val pending: HarkAction? = null,
    val targetTitle: String? = null,
    val focusedNote: FocusedNote? = null,
    val error: String? = null,
)

/**
 * Talk flow: record mic audio → Whisper transcription → HarkAction LLM processing → keep.
 */
class TalkViewModel(
    private val repo: HarkRepository,
    private val harkService: HarkService,
    private val client: OpenAiClient,
    private val recorderFactory: () -> AudioRecorder,
    private val focusedNoteId: Long? = null,
) : ViewModel() {

    private val _ui = MutableStateFlow(TalkUiState())
    val ui: StateFlow<TalkUiState> = _ui.asStateFlow()

    private var recorder: AudioRecorder? = null
    private var meterJob: Job? = null
    private var transcript = ""

    init {
        if (focusedNoteId != null) {
            viewModelScope.launch {
                val focused = repo.focusedNoteOf(focusedNoteId)
                _ui.update { it.copy(focusedNote = focused) }
            }
        }
    }

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
        _ui.update { it.copy(phase = TalkPhase.LISTENING, pending = null, error = null) }
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

    fun toggleExtractTasks() {
        _ui.update { it.copy(extractTasks = !it.extractTasks) }
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

            val notes = repo.recentNoteRefs()
            val focused = _ui.value.focusedNote
            val willShelf = focused == null && text.length > HarkRepository.SHELF_THRESHOLD
            val extract = _ui.value.extractTasks && !willShelf

            val action = harkService.process(
                transcript = text,
                extractTasks = extract,
                notes = notes,
                focusedNote = focused,
            )

            // Resolve target title for UI confirmation
            val targetTitle = if (action.action != Action.CREATE && action.targetNoteId != null) {
                if (focused?.id == action.targetNoteId) {
                    focused.title
                } else {
                    notes.find { it.id == action.targetNoteId }?.title
                }
            } else {
                null
            }

            _ui.update {
                it.copy(
                    phase = TalkPhase.RESULT,
                    pending = action,
                    targetTitle = targetTitle,
                )
            }
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

    fun keep(onDone: (Long) -> Unit) {
        val action = _ui.value.pending ?: return
        val heard = transcript
        viewModelScope.launch {
            val noteId = repo.applyAction(action, heard.ifBlank { action.body }, Source.SPOKEN)
            onDone(noteId)
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

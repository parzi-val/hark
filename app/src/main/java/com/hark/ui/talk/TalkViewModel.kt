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
    val checklistOnly: Boolean = false,
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
    private var baseTranscript = "" // accumulated text from prior "Continue" segments
    private var kept = false // one-shot guard so a double-tap / double-back can't save twice

    init {
        if (focusedNoteId != null) {
            viewModelScope.launch {
                val focused = repo.focusedNoteOf(focusedNoteId)
                _ui.update { it.copy(focusedNote = focused) }
            }
        }
    }

    /** Start a fresh capture. Call only after RECORD_AUDIO is granted. */
    fun start() {
        baseTranscript = ""
        beginRecording()
    }

    /** Keep the current capture and record more — the next tidy combines both segments. */
    fun continueTalking() {
        baseTranscript = transcript
        beginRecording()
    }

    private fun beginRecording() {
        stopMeter()
        kept = false
        transcript = ""
        try {
            recorder = recorderFactory().also { it.start() }
        } catch (e: Exception) {
            _ui.value = TalkUiState(phase = TalkPhase.ERROR, error = "Couldn't start the microphone.")
            return
        }
        _ui.update { it.copy(phase = TalkPhase.LISTENING, pending = null, error = null, transcript = "") }
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

    fun toggleChecklistOnly() {
        _ui.update { it.copy(checklistOnly = !it.checklistOnly) }
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
            transcript = if (baseTranscript.isBlank()) text else "$baseTranscript\n\n$text"
            _ui.update { it.copy(transcript = transcript, phase = TalkPhase.TIDYING) }

            val notes = repo.recentNoteRefs()
            val focused = _ui.value.focusedNote
            val checklist = _ui.value.checklistOnly
            val willShelf = focused == null && !checklist && transcript.length > HarkRepository.SHELF_THRESHOLD
            val extract = checklist || (_ui.value.extractTasks && !willShelf)

            val action = harkService.process(
                transcript = transcript,
                extractTasks = extract,
                notes = notes,
                focusedNote = focused,
                checklistOnly = checklist,
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

    /** Surface a terminal error (e.g. microphone permission denied). */
    fun fail(message: String) {
        stopMeter()
        recorder?.cancel()
        recorder = null
        _ui.update { it.copy(phase = TalkPhase.ERROR, error = message) }
    }

    fun keep(onDone: (Long) -> Unit) {
        if (kept) return
        val action = _ui.value.pending ?: return
        kept = true
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

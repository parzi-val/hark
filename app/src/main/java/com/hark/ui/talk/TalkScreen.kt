package com.hark.ui.talk

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hark.domain.Action
import com.hark.domain.HarkAction
import com.hark.ui.components.HarkMarkdown
import com.hark.ui.components.ListeningWave
import com.hark.ui.components.MetaLabel
import com.hark.ui.components.SectionLabel
import com.hark.ui.harkViewModel
import com.hark.ui.theme.Hark
import com.hark.ui.theme.HarkType

@Composable
fun TalkScreen(
    onClose: () -> Unit,
    onKept: (Long) -> Unit,
    focusedNoteId: Long? = null,
) {
    val vm: TalkViewModel = harkViewModel(key = "talk-${focusedNoteId ?: "new"}") {
        TalkViewModel(it.repository, it.harkService, it.openAiClient, it::newAudioRecorder, focusedNoteId)
    }
    val state by vm.ui.collectAsStateWithLifecycle()
    val c = Hark.colors
    val context = LocalContextCompat()

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.start() else vm.fail("Microphone permission is needed to talk.")
    }
    fun ensureListening() {
        if (hasAudioPermission(context)) vm.start() else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    LaunchedEffect(Unit) { ensureListening() }

    // Leaving with a pending result auto-saves it (then just goes back); Discard is the only
    // path that drops it. Covers both the top-bar Close and the system back gesture.
    val handleClose = {
        if (state.phase == TalkPhase.RESULT && state.pending != null) {
            vm.keep { onClose() }
        } else {
            onClose()
        }
    }
    BackHandler { handleClose() }

    Column(Modifier.fillMaxSize().background(c.paper)) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetaLabel("↩ Close", color = c.inkMuted, modifier = Modifier.clickable { handleClose() })
            MetaLabel(phaseLabel(state.phase), color = c.inkFaint)
        }

        // Body
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val pending = state.pending
            when {
                state.phase == TalkPhase.ERROR ->
                    Text(state.error ?: "Something went wrong.", style = HarkType.bodyRelaxed, color = c.rust)

                state.phase == TalkPhase.RESULT && pending != null ->
                    ResultView(pending, state.targetTitle)

                else -> Text(
                    text = state.transcript.ifBlank { "Listening… say what's on your mind." },
                    style = HarkType.bodyRelaxed,
                    color = if (state.transcript.isBlank()) c.inkFaint else c.inkMuted,
                )
            }
        }

        // Controls
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state.phase) {
                TalkPhase.LISTENING -> {
                    ListeningWave(level = state.level, color = c.rust, modifier = Modifier.height(44.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MetaLabel(formatElapsed(state.elapsed), color = c.inkFaint)
                        MetaLabel(
                            text = (if (state.checklistOnly) "● " else "○ ") + "Checklist only",
                            color = if (state.checklistOnly) c.rust else c.inkFaint,
                            modifier = Modifier.clickable { vm.toggleChecklistOnly() },
                        )
                        if (!state.checklistOnly) {
                            MetaLabel(
                                text = "Extract tasks: " + if (state.extractTasks) "On" else "Off",
                                color = if (state.extractTasks) c.rust else c.inkFaint,
                                modifier = Modifier.clickable { vm.toggleExtractTasks() },
                            )
                        }
                    }
                    FilledPill("Stop & tidy", onClick = vm::stopAndTidy)
                }

                TalkPhase.TRANSCRIBING ->
                    Text("Transcribing…", style = HarkType.label, color = c.inkMuted, modifier = Modifier.height(44.dp).padding(top = 14.dp))

                TalkPhase.TIDYING ->
                    Text("Tidying…", style = HarkType.label, color = c.inkMuted, modifier = Modifier.height(44.dp).padding(top = 14.dp))

                TalkPhase.RESULT -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedPill("Discard", Modifier.weight(1f), danger = true, onClick = onClose)
                    OutlinedPill("Continue", Modifier.weight(1f), onClick = vm::continueTalking)
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(c.ink)
                            .clickable { vm.keep(onKept) },
                        contentAlignment = Alignment.Center,
                    ) {
                        CheckMark(color = c.paper, modifier = Modifier.size(22.dp))
                    }
                }

                TalkPhase.ERROR -> FilledPill("Try again", onClick = { ensureListening() })
                TalkPhase.IDLE -> Unit
            }
        }
    }
}

@Composable
private fun ResultView(action: HarkAction, targetTitle: String?) {
    val c = Hark.colors

    val headerText = when (action.action) {
        Action.APPEND -> "→ Appending to \"${targetTitle ?: "a note"}\""
        Action.EDIT -> "→ Editing \"${targetTitle ?: "a note"}\""
        Action.CREATE -> "New note"
    }

    SectionLabel(headerText)

    if (action.action != Action.APPEND && !action.title.isNullOrBlank()) {
        Text(action.title, style = HarkType.title, color = c.ink)
    }

    if (action.body.isNotBlank()) {
        HarkMarkdown(action.body)
    }

    if (action.tasks.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            action.tasks.forEach { t ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.padding(top = 3.dp).size(16.dp).clip(RoundedCornerShape(3.dp))
                            .border(1.dp, c.checkboxBorder, RoundedCornerShape(3.dp)),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(t.title, style = HarkType.item, color = c.ink, textDecoration = TextDecoration.None)
                        t.dueHint?.let { MetaLabel("heard \"$it\"", color = c.rust) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilledPill(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Hark.colors
    Box(
        modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp)).background(c.ink).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(label, style = HarkType.label, color = c.paper) }
}

@Composable
private fun OutlinedPill(label: String, modifier: Modifier = Modifier, danger: Boolean = false, onClick: () -> Unit) {
    val c = Hark.colors
    val tint = if (danger) c.rust else c.inkMuted
    val border = if (danger) c.rust.copy(alpha = 0.5f) else c.ink.copy(alpha = 0.16f)
    Box(
        modifier.height(54.dp).clip(RoundedCornerShape(27.dp))
            .border(1.dp, border, RoundedCornerShape(27.dp)).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text(label, style = HarkType.label, color = tint) }
}

/** A clean two-stroke checkmark drawn to fit Hark's hand-drawn line motifs (no icon dependency). */
@Composable
private fun CheckMark(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.22f, h * 0.52f)
            lineTo(w * 0.42f, h * 0.70f)
            lineTo(w * 0.78f, h * 0.30f)
        }
        drawPath(path, color = color, style = Stroke(width = w * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun phaseLabel(phase: TalkPhase): String = when (phase) {
    TalkPhase.LISTENING -> "Listening"
    TalkPhase.TRANSCRIBING -> "Transcribing"
    TalkPhase.TIDYING -> "Thinking"
    TalkPhase.RESULT -> "Tidied"
    TalkPhase.ERROR -> "—"
    TalkPhase.IDLE -> ""
}

private fun formatElapsed(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

private fun hasAudioPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

@Composable
private fun LocalContextCompat(): Context = androidx.compose.ui.platform.LocalContext.current

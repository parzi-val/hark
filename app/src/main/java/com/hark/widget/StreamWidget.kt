package com.hark.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.hark.HarkApp
import com.hark.MainActivity
import com.hark.R
import com.hark.ai.SettingsStore
import com.hark.ai.ThemeMode
import com.hark.ai.WidgetTheme
import com.hark.data.local.HarkDatabase
import com.hark.data.local.NoteEntity
import com.hark.data.local.TaskEntity
import com.hark.domain.StreamItem
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlin.math.ceil

/** One rendered line in the widget: a note header, a task, or a divider between items. */
private sealed interface WidgetRow {
    data class NoteHeader(val note: NoteEntity) : WidgetRow
    data class TaskLine(val task: TaskEntity, val nested: Boolean) : WidgetRow
    data object LineDivider : WidgetRow
}

class StreamWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as? HarkApp
        val db = HarkDatabase.get(context.applicationContext)
        val settingsStore = app?.container?.settingsStore ?: SettingsStore(context.applicationContext)
        val accentFont = ResourcesCompat.getFont(context, R.font.quicksand) ?: Typeface.MONOSPACE

        val initialNotes = db.noteDao().observeAll().first()
        val initialTasks = db.taskDao().observeAll().first()
        val initialSettings = settingsStore.settings.value

        provideContent {
            val settings by settingsStore.settings.collectAsState(initial = initialSettings)

            val isDark = when (settings.widgetTheme) {
                WidgetTheme.DARK -> true
                WidgetTheme.PAPER -> false
                WidgetTheme.MATCH_APP -> settings.themeMode == ThemeMode.DARK
            }

            val alpha = (settings.widgetOpacity / 100f).coerceIn(0.2f, 1f)

            // Exact match to Hark app palette:
            // Light: PaperLight #F4F2ED, InkLight #1C1B19, RustLight #8A4B34
            // Dark:  PaperDark  #33342F, InkDark  #ECEAE4, RustDark  #D99F83
            val basePaper = if (isDark) Color(0xFF33342F) else Color(0xFFF4F2ED)
            val paper = basePaper.copy(alpha = alpha)

            val ink = if (isDark) Color(0xFFECEAE4) else Color(0xFF1C1B19)
            val inkFaint = ink.copy(alpha = if (isDark) 0.50f else 0.45f)
            val inkHairline = ink.copy(alpha = if (isDark) 0.15f else 0.12f)
            val rust = if (isDark) Color(0xFFD99F83) else Color(0xFF8A4B34)

            val notes by db.noteDao().observeAll().collectAsState(initial = initialNotes)
            val tasks by db.taskDao().observeAll().collectAsState(initial = initialTasks)

            val openCount = tasks.count { !it.done }
            val childrenByNote = tasks.filter { it.sourceNoteId != null }.groupBy { it.sourceNoteId }
            val items = buildList {
                notes.filter { !it.shelf && !it.archived }.forEach { note -> add(StreamItem.Note(note, childrenByNote[note.id].orEmpty().sortedBy { it.createdAt })) }
                tasks.filter { it.sourceNoteId == null }.forEach { add(StreamItem.Task(it)) }
            }.sortedWith(
                compareByDescending<StreamItem> { (it as? StreamItem.Note)?.note?.pinnedToWidget ?: false }
                    .thenByDescending { it.createdAt }
            )

            val rows = buildList {
                items.forEachIndexed { index, item ->
                    if (index > 0) add(WidgetRow.LineDivider)
                    when (item) {
                        is StreamItem.Note -> {
                            add(WidgetRow.NoteHeader(item.note))
                            for (t in item.tasks) {
                                add(WidgetRow.TaskLine(t, nested = true))
                            }
                        }
                        is StreamItem.Task -> add(WidgetRow.TaskLine(item.task, nested = false))
                    }
                }
            }

            val now = LocalDate.now()
            val dateText = "${now.dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.getDefault())} ${now.dayOfMonth}"
            val word = if (settings.showWordOfTheDay) {
                app?.container?.lexiconRepository?.getWordForDate(now)
            } else null

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(24.dp)
                    .background(paper)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                // Header (Pinned at top)
                Row(
                    modifier = GlanceModifier.fillMaxWidth().clickable(launch(context, MainActivity::class.java)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MonoLabel(context, accentFont, "Hark · $openCount open", 13f, ink.toArgb())
                    Spacer(GlanceModifier.defaultWeight())
                    if (word != null) {
                        MonoLabel(context, accentFont, "❖ ${word.word.replaceFirstChar { it.uppercase() }} · $dateText", 12f, rust.toArgb())
                    } else {
                        MonoLabel(context, accentFont, dateText, 13f, inkFaint.toArgb())
                    }
                }
                Spacer(GlanceModifier.height(10.dp))

                // Scrollable LazyColumn of notes and tasks
                if (rows.isEmpty()) {
                    Box(modifier = GlanceModifier.defaultWeight().fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        Text("Nothing open. Tap TALK to capture.", style = TextStyle(ColorProvider(inkFaint), fontSize = 14.5.sp, fontFamily = FontFamily.Serif))
                    }
                } else {
                    LazyColumn(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                        items(rows) { row ->
                            when (row) {
                                is WidgetRow.LineDivider -> {
                                    Box(
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .padding(top = 5.dp, bottom = 5.dp, end = 20.dp)
                                            .height(1.dp)
                                            .background(inkHairline)
                                    ) {}
                                }
                                is WidgetRow.NoteHeader -> NoteRow(context, row.note, ink, rust, inkFaint)
                                is WidgetRow.TaskLine -> TaskRow(context, accentFont, row.task, nested = row.nested, ink = ink, rust = rust, inkFaint = inkFaint, isDark = isDark)
                            }
                        }
                    }
                }

                // Optional bottom toolbar (Pinned at bottom)
                if (settings.widgetShowToolbar) {
                    Spacer(GlanceModifier.height(4.dp))
                    Box(GlanceModifier.fillMaxWidth().height(1.dp).background(inkHairline)) {}
                    Spacer(GlanceModifier.height(6.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconCell(GlanceModifier.defaultWeight(), "＋", 21f, context, MainActivity::class.java, ink)
                        VDivider(inkHairline)
                        TalkCell(GlanceModifier.defaultWeight(), context, accentFont, rust)
                        VDivider(inkHairline)
                        MonoCell(GlanceModifier.defaultWeight(), context, accentFont, "Today", MainActivity::class.java, ink)
                        VDivider(inkHairline)
                        IconCell(GlanceModifier.defaultWeight(), "⌕", 20f, context, MainActivity::class.java, ink)
                    }
                }
            }
        }
    }
}

private fun launch(context: Context, target: Class<*>) = actionStartActivity(Intent(context, target))

@Composable
private fun NoteRow(context: Context, note: NoteEntity, ink: Color, rust: Color, inkFaint: Color) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(top = 3.dp, bottom = 3.dp, end = 20.dp)
            .clickable(launch(context, MainActivity::class.java)),
        verticalAlignment = Alignment.Top,
    ) {
        // Dash prefix slot (18dp width, centered vertically with the title's line-height)
        Box(
            modifier = GlanceModifier.width(18.dp).height(24.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = GlanceModifier
                    .width(13.dp)
                    .height(1.5.dp)
                    .background(ColorProvider(if (note.pinnedToWidget) rust else inkFaint)),
            ) {}
        }
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            val displayTitle = if (note.pinnedToWidget) "★ ${note.title}" else note.title
            Text(
                displayTitle,
                maxLines = 1,
                style = TextStyle(ColorProvider(ink), fontSize = 15.5.sp, fontFamily = FontFamily.Serif),
            )
            if (note.body.isNotBlank()) {
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    note.body,
                    maxLines = 2,
                    style = TextStyle(ColorProvider(inkFaint), fontSize = 13.5.sp, fontFamily = FontFamily.Serif),
                )
            }
        }
    }
}

@Composable
private fun TaskRow(
    context: Context,
    mono: Typeface,
    task: TaskEntity,
    nested: Boolean,
    ink: Color,
    rust: Color,
    inkFaint: Color,
    isDark: Boolean,
) {
    val checkDrawable = if (isDark) R.drawable.widget_check_dark else R.drawable.widget_check
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp, end = 20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (nested) {
            Spacer(GlanceModifier.width(26.dp))
        }
        // Checkbox slot (18dp width, perfectly aligned with the Note dash)
        Box(
            modifier = GlanceModifier.width(18.dp).height(24.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = GlanceModifier
                    .size(15.dp)
                    .background(ImageProvider(checkDrawable))
                    .clickable(
                        actionRunCallback<ToggleTaskAction>(
                            actionParametersOf(ToggleTaskAction.TaskIdKey to task.id, ToggleTaskAction.DoneKey to task.done),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (task.done) Box(GlanceModifier.size(7.5.dp).cornerRadius(1.dp).background(rust)) {}
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        Text(
            task.title,
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight().clickable(launch(context, MainActivity::class.java)),
            style = TextStyle(
                ColorProvider(if (task.done) inkFaint else ink),
                fontSize = 15.sp,
                fontFamily = FontFamily.Serif,
                textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
            ),
        )
        task.dueHint?.let { hint ->
            Spacer(GlanceModifier.width(6.dp))
            MonoLabel(context, mono, hint, 10.5f, rust.toArgb())
            Spacer(GlanceModifier.width(2.dp))
        }
    }
}

@Composable
private fun IconCell(modifier: GlanceModifier, glyph: String, sizeSp: Float, context: Context, target: Class<*>, ink: Color) {
    Box(modifier = modifier.height(40.dp).clickable(launch(context, target)), contentAlignment = Alignment.Center) {
        Text(glyph, style = TextStyle(ColorProvider(ink), fontSize = sizeSp.sp, fontFamily = FontFamily.Monospace))
    }
}

@Composable
private fun MonoCell(modifier: GlanceModifier, context: Context, mono: Typeface, label: String, target: Class<*>, ink: Color) {
    Box(modifier = modifier.height(40.dp).clickable(launch(context, target)), contentAlignment = Alignment.Center) {
        MonoLabel(context, mono, label, 12.5f, ink.toArgb())
    }
}

@Composable
private fun TalkCell(modifier: GlanceModifier, context: Context, mono: Typeface, rust: Color) {
    Box(modifier = modifier.height(40.dp).clickable(launch(context, TalkTrampolineActivity::class.java)), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.Bottom) {
            Box(GlanceModifier.width(2.dp).height(12.dp).background(rust)) {}
            Spacer(GlanceModifier.width(2.dp))
            Box(GlanceModifier.width(2.dp).height(7.dp).background(rust)) {}
            Spacer(GlanceModifier.width(2.dp))
            Box(GlanceModifier.width(2.dp).height(10.dp).background(rust)) {}
            Spacer(GlanceModifier.width(6.dp))
            MonoLabel(context, mono, "Talk", 12.5f, rust.toArgb())
        }
    }
}

@Composable
private fun VDivider(inkHairline: Color) {
    Box(GlanceModifier.width(1.dp).height(22.dp).background(inkHairline)) {}
}

/** Draws [text] with the real [typeface] to a bitmap and shows it. */
@Composable
private fun MonoLabel(context: Context, typeface: Typeface, text: String, sizeSp: Float, colorArgb: Int) {
    val density = context.resources.displayMetrics.density
    val bitmap = remember(text, sizeSp, colorArgb) { textBitmap(density, typeface, text, sizeSp, colorArgb, letterSpacing = 0.12f) }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = text,
        modifier = GlanceModifier.width((bitmap.width / density).dp).height((bitmap.height / density).dp),
    )
}

private fun textBitmap(density: Float, typeface: Typeface, text: String, sizeSp: Float, colorArgb: Int, letterSpacing: Float): Bitmap {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = sizeSp * density
        color = colorArgb
        this.letterSpacing = letterSpacing
    }
    val pad = (letterSpacing * paint.textSize * 2).toInt() + 10
    val width = (ceil(paint.measureText(text).toDouble()).toInt() + pad).coerceAtLeast(1)
    val fm = paint.fontMetrics
    val height = ceil((fm.bottom - fm.top).toDouble()).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).drawText(text, 0f, -fm.top, paint)
    return bitmap
}

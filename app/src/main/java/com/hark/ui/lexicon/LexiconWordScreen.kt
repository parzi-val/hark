package com.hark.ui.lexicon

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hark.HarkApp
import com.hark.ui.components.MetaLabel
import com.hark.ui.components.SectionLabel
import com.hark.ui.theme.Hark
import com.hark.ui.theme.HarkType

/**
 * Full entry page for a single Lexicon word — the deep context that used to live in the
 * card's inline expansion, now on its own screen.
 */
@Composable
fun LexiconWordScreen(
    wordId: String,
    onClose: () -> Unit,
    onOpenArchive: () -> Unit = {},
) {
    val c = Hark.colors
    val repo = (LocalContext.current.applicationContext as HarkApp).container.lexiconRepository
    val word = remember(wordId) { repo.getWordById(wordId) }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.paper)
    ) {
        // Top bar — matches Shelf / Archive
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetaLabel("↩ Back", color = c.inkMuted, modifier = Modifier.clickable { onClose() })
            MetaLabel("Archive →", color = c.rust, modifier = Modifier.clickable { onOpenArchive() })
        }

        if (word == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Word not found.", style = HarkType.secondary, color = c.inkFaint)
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 26.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Tier
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("❖", style = HarkType.label, color = if (word.tier >= 4) c.rust else c.inkMuted)
                    SectionLabel(
                        "Tier ${word.tier} ${word.tierLabel}",
                        color = if (word.tier >= 4) c.rust else c.inkMuted,
                    )
                }

                // Word + phonetic + part of speech
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = word.word,
                        style = HarkType.title.copy(fontSize = 34.sp, lineHeight = 38.sp),
                        color = c.ink,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (word.phonetic.isNotBlank()) {
                            Text(word.phonetic, style = HarkType.meta, color = c.inkFaint)
                        }
                        Text(
                            word.pos,
                            style = HarkType.secondary.copy(fontStyle = FontStyle.Italic),
                            color = c.inkMuted,
                        )
                    }
                }

                HorizontalDivider(color = c.inkHairline)

                // Definition
                Text(word.definition, style = HarkType.bodyRelaxed, color = c.ink)

                // Canonical example
                if (word.canonicalExample.isNotBlank()) {
                    Text(
                        text = "\"${word.canonicalExample}\"",
                        style = HarkType.bodyRelaxed.copy(fontStyle = FontStyle.Italic),
                        color = c.ink.copy(alpha = 0.9f),
                    )
                }

                // Use it when
                if (word.useWhen.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionLabel("Use it when", color = c.rust)
                        Text(word.useWhen, style = HarkType.secondary, color = c.inkMuted)
                    }
                }

                // Contrast with a near-synonym
                if (word.contrastWord.isNotBlank() && word.contrastDistinction.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionLabel("Not quite the same as: ${word.contrastWord}", color = c.inkMuted)
                        Text(word.contrastDistinction, style = HarkType.secondary, color = c.inkMuted)
                    }
                }

                // Related
                if (word.nearSynonyms.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SectionLabel("Related", color = c.inkFaint)
                        Text(word.nearSynonyms.joinToString(" · "), style = HarkType.meta, color = c.inkFaint)
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

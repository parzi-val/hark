package com.hark.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hark.domain.LexiconWord
import com.hark.ui.theme.Hark
import com.hark.ui.theme.HarkType

/**
 * A compact Word of the Day (λέξις) card. Tapping it opens the full entry page —
 * the deep context (example, use-when, contrast, synonyms) lives there, not inline.
 */
@Composable
fun LexiconCard(
    word: LexiconWord,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val c = Hark.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.paperRaised)
            .border(1.dp, c.inkHairline, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header: Tier label & "open entry" affordance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "❖",
                        style = HarkType.label,
                        color = if (word.tier >= 4) c.rust else c.inkMuted,
                    )
                    SectionLabel(
                        text = "λέξις · TIER ${word.tier} ${word.tierLabel}",
                        color = if (word.tier >= 4) c.rust else c.inkMuted,
                    )
                }

                MetaLabel(text = "DETAILS →", color = c.inkFaint)
            }

            // Word on its own line; phonetic + part of speech below it. Keeping them on one
            // row let a long word squeeze the pos into a vertical letter-stack.
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = word.word,
                    style = HarkType.noteTitle.copy(fontSize = 22.sp),
                    color = c.ink,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (word.phonetic.isNotBlank()) {
                        Text(
                            text = word.phonetic,
                            style = HarkType.meta,
                            color = c.inkFaint,
                        )
                    }
                    Text(
                        text = word.pos,
                        style = HarkType.secondary.copy(fontStyle = FontStyle.Italic, fontSize = 13.sp),
                        color = c.inkMuted,
                        maxLines = 1,
                    )
                }
            }

            // Razor-sharp definition (single-line teaser; full context on the entry page)
            Text(
                text = word.definition,
                style = HarkType.body,
                color = c.ink.copy(alpha = 0.88f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

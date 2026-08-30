package com.hark.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.hark.ui.theme.Hark
import com.hark.ui.theme.HarkType

/**
 * Wraps a task's tappable area so a long-press opens a small context menu. A single
 * [combinedClickable] handles both tap ([onClick]) and long-press, so it never fights a nested
 * clickable. Used by the Stream and Today task rows for "Mark as deferred".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskTapMenu(
    deferred: Boolean,
    onClick: () -> Unit,
    onToggleDeferred: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val c = Hark.colors
    Box(modifier) {
        Box(
            Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = { open = true },
            ),
        ) {
            content()
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = c.paper,
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        if (deferred) "Move to open" else "Mark as deferred",
                        style = HarkType.item,
                        color = c.ink,
                    )
                },
                onClick = {
                    onToggleDeferred()
                    open = false
                },
            )
        }
    }
}

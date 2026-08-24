package com.hark.ui.task

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hark.data.local.TaskEntity
import com.hark.ui.components.MetaLabel
import com.hark.ui.components.SectionLabel
import com.hark.ui.theme.Hark
import com.hark.ui.theme.HarkType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskDialog(
    task: TaskEntity,
    onDismiss: () -> Unit,
    onSave: (title: String, dueHint: String?) -> Unit,
    onDelete: () -> Unit,
) {
    val c = Hark.colors
    var title by remember(task) { mutableStateOf(task.title) }
    var dueHint by remember(task) { mutableStateOf(task.dueHint.orEmpty()) }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(c.paper)
                .border(1.dp, c.inkHairline, RoundedCornerShape(24.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel("EDIT TASK")
                MetaLabel("DELETE", color = c.rust, modifier = Modifier.clickable { onDelete() })
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("TASK DESCRIPTION")
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.ink,
                        unfocusedBorderColor = c.checkboxBorder,
                        focusedTextColor = c.ink,
                        unfocusedTextColor = c.ink,
                    ),
                    textStyle = HarkType.item,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionLabel("DUE / REMINDER (OPTIONAL)")
                OutlinedTextField(
                    value = dueHint,
                    onValueChange = { dueHint = it },
                    placeholder = { Text("e.g. today 5pm, tomorrow, friday", color = c.inkFaint, style = HarkType.body) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.ink,
                        unfocusedBorderColor = c.checkboxBorder,
                        focusedTextColor = c.ink,
                        unfocusedTextColor = c.ink,
                    ),
                    textStyle = HarkType.body,
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .border(1.dp, c.inkHairline, RoundedCornerShape(22.dp))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("CANCEL", style = HarkType.label, color = c.inkMuted)
                }

                val hasContent = title.isNotBlank()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (hasContent) c.ink else c.inkHairline)
                        .clickable(enabled = hasContent) {
                            onSave(title.trim(), dueHint.trim().ifBlank { null })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("SAVE", style = HarkType.label, color = if (hasContent) c.paper else c.inkFaint)
                }
            }
        }
    }
}

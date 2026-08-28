package com.hark.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hark.ai.AiSettings
import com.hark.ai.ThemeMode
import com.hark.ai.WidgetTheme
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.hark.HarkApp
import com.hark.sync.GoogleAuth
import com.hark.ui.components.MetaLabel
import com.hark.ui.components.SectionLabel
import com.hark.ui.harkViewModel
import com.hark.ui.theme.Hark
import com.hark.ui.theme.HarkType
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val vm: SettingsViewModel = harkViewModel { SettingsViewModel(it.settingsStore) }
    val currentSettings by vm.settings.collectAsStateWithLifecycle()
    val c = Hark.colors

    var apiKey by remember(currentSettings) { mutableStateOf(currentSettings.apiKey) }
    var baseUrl by remember(currentSettings) { mutableStateOf(currentSettings.baseUrl) }
    var model by remember(currentSettings) { mutableStateOf(currentSettings.model) }
    var showApiKey by remember { mutableStateOf(false) }
    var savedFeedback by remember { mutableStateOf(false) }

    // ---- Drive sync ----
    val ctx = LocalContext.current
    val sync = remember { (ctx.applicationContext as HarkApp).container.syncManager }
    val scope = rememberCoroutineScope()
    var signedIn by remember { mutableStateOf(sync.isEnabled) }
    var optIn by remember { mutableStateOf(sync.isApiKeySynced) }
    var syncBusy by remember { mutableStateOf(false) }
    var syncMsg by remember { mutableStateOf("") }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            scope.launch {
                try {
                    val authRes = GoogleAuth.resultFromIntent(ctx, data)
                    if (authRes.accessToken != null) {
                        signedIn = true
                        sync.onSignedIn()
                        syncMsg = "Synced."
                    } else {
                        syncMsg = "Sign-in failed — try again."
                    }
                } catch (e: Exception) {
                    syncMsg = "Sign-in failed — try again."
                } finally {
                    syncBusy = false
                }
            }
        } else {
            syncBusy = false
            syncMsg = "Sign-in cancelled."
        }
    }

    val startSignIn = {
        scope.launch {
            syncBusy = true
            syncMsg = ""
            try {
                val res = GoogleAuth.authorize(ctx)
                val pi = res.pendingIntent
                if (res.hasResolution() && pi != null) {
                    consentLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    // syncBusy stays true until the consent result returns
                } else {
                    signedIn = true
                    sync.onSignedIn()
                    syncMsg = "Synced."
                    syncBusy = false
                }
            } catch (e: Exception) {
                syncMsg = "Sign-in failed — try again."
                syncBusy = false
            }
        }
        Unit
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.paper)
    ) {
        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetaLabel("↩ Back", color = c.inkMuted, modifier = Modifier.clickable { onClose() })
            MetaLabel("Settings", color = c.inkFaint)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // App Appearance section (LIVE UPDATING)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("APP THEME")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ThemeMode.entries.forEach { mode ->
                        val isSelected = mode == currentSettings.themeMode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(if (isSelected) c.ink else c.paper)
                                .border(1.dp, if (isSelected) c.ink else c.inkHairline, RoundedCornerShape(22.dp))
                                .clickable {
                                    vm.setThemeMode(mode)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = mode.name,
                                style = HarkType.label,
                                color = if (isSelected) c.paper else c.inkMuted,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = c.inkHairline)

            // Widget Appearance section (LIVE UPDATING)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionLabel("WIDGET THEME")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        WidgetTheme.PAPER to "PAPER",
                        WidgetTheme.DARK to "DARK",
                        WidgetTheme.MATCH_APP to "MATCH APP",
                    ).forEach { (theme, label) ->
                        val isSelected = theme == currentSettings.widgetTheme
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) c.ink else c.paper)
                                .border(1.dp, if (isSelected) c.ink else c.inkHairline, RoundedCornerShape(20.dp))
                                .clickable {
                                    vm.setWidgetTheme(theme)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                style = HarkType.label,
                                color = if (isSelected) c.paper else c.inkMuted,
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionLabel("WIDGET BACKGROUND OPACITY")
                        MetaLabel("${currentSettings.widgetOpacity}%", color = c.rust)
                    }
                    Slider(
                        value = currentSettings.widgetOpacity.toFloat(),
                        onValueChange = { vm.setWidgetOpacity(it.toInt()) },
                        valueRange = 20f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = c.ink,
                            activeTrackColor = c.ink,
                            inactiveTrackColor = c.checkboxBorder.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SectionLabel("WIDGET BOTTOM TOOLBAR")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    listOf(true to "SHOW TOOLBAR", false to "HIDE TOOLBAR").forEach { (show, label) ->
                        val isSelected = currentSettings.widgetShowToolbar == show
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) c.ink else c.paper)
                                .border(1.dp, if (isSelected) c.ink else c.inkHairline, RoundedCornerShape(20.dp))
                                .clickable {
                                    vm.setWidgetShowToolbar(show)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                style = HarkType.label,
                                color = if (isSelected) c.paper else c.inkMuted,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = c.inkHairline)

            // Vocabulary / Lexicon Section
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel("WORD OF THE DAY (λέξις)")
                Text(
                    "Show an elevated daily vocabulary card on the Today screen for conceptual precision.",
                    style = HarkType.secondary,
                    color = c.inkMuted,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    listOf(true to "ENABLED", false to "DISABLED").forEach { (enabled, label) ->
                        val isSelected = currentSettings.showWordOfTheDay == enabled
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) c.ink else c.paper)
                                .border(1.dp, if (isSelected) c.ink else c.inkHairline, RoundedCornerShape(20.dp))
                                .clickable {
                                    vm.setShowWordOfTheDay(enabled)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                style = HarkType.label,
                                color = if (isSelected) c.paper else c.inkMuted,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = c.inkHairline)

            // Sync (Google Drive appData)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel("SYNC")
                Text(
                    "Keep your notes in step across devices through your own private Google Drive. Optional — Hark works fully offline without it.",
                    style = HarkType.secondary,
                    color = c.inkMuted,
                )

                if (!signedIn) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(c.ink)
                            .clickable(enabled = !syncBusy) { startSignIn() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (syncBusy) "CONNECTING…" else "SIGN IN WITH GOOGLE",
                            style = HarkType.label,
                            color = c.paper,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MetaLabel("✓ Connected · Drive", color = c.rust)
                        MetaLabel(
                            "SIGN OUT",
                            color = c.inkMuted,
                            modifier = Modifier.clickable {
                                sync.signOut()
                                signedIn = false
                                syncMsg = ""
                            },
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .border(1.dp, c.inkHairline, RoundedCornerShape(22.dp))
                            .clickable(enabled = !syncBusy) {
                                scope.launch {
                                    syncBusy = true
                                    syncMsg = ""
                                    try {
                                        sync.syncNow()
                                        syncMsg = "Synced."
                                    } catch (e: Exception) {
                                        syncMsg = "Sync failed — sign in again?"
                                    } finally {
                                        syncBusy = false
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (syncBusy) "SYNCING…" else "SYNC NOW", style = HarkType.label, color = c.ink)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.paperRaised)
                            .border(1.dp, c.inkHairline, RoundedCornerShape(12.dp))
                            .clickable {
                                val next = !optIn
                                optIn = next
                                sync.isApiKeySynced = next
                            }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Also sync my API key", style = HarkType.secondary, color = c.ink)
                            Text("Stored only in your private Drive folder.", style = HarkType.meta, color = c.inkFaint)
                        }
                        Box(
                            modifier = Modifier
                                .width(44.dp)
                                .height(26.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(if (optIn) c.rust else c.inkHairline),
                            contentAlignment = if (optIn) Alignment.CenterEnd else Alignment.CenterStart,
                        ) {
                            Box(
                                Modifier
                                    .padding(3.dp)
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(c.paper),
                            )
                        }
                    }
                }

                if (syncMsg.isNotBlank()) {
                    Text(syncMsg, style = HarkType.secondary, color = c.rust)
                }
            }

            HorizontalDivider(color = c.inkHairline)

            Text("AI Configuration", style = HarkType.title, color = c.ink)

            Text(
                "Hark connects directly to any OpenAI-compatible API to tidy your notes and extract tasks. Default provider is Groq for low-latency responses.",
                style = HarkType.secondary,
                color = c.inkMuted,
            )

            // API Key field
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("API KEY")
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        savedFeedback = false
                    },
                    placeholder = { Text("gsk_...", color = c.inkFaint, style = HarkType.body) },
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        Text(
                            text = if (showApiKey) "HIDE" else "SHOW",
                            style = HarkType.label,
                            color = c.inkMuted,
                            modifier = Modifier
                                .clickable { showApiKey = !showApiKey }
                                .padding(end = 12.dp),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.ink,
                        unfocusedBorderColor = c.checkboxBorder,
                        focusedTextColor = c.ink,
                        unfocusedTextColor = c.ink,
                    ),
                    textStyle = HarkType.body,
                )
                MetaLabel(
                    "Get a free API key at console.groq.com",
                    color = if (apiKey.isBlank()) c.rust else c.inkFaint,
                )
            }

            // Base URL field
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("BASE URL")
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                        savedFeedback = false
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.ink,
                        unfocusedBorderColor = c.checkboxBorder,
                        focusedTextColor = c.ink,
                        unfocusedTextColor = c.ink,
                    ),
                    textStyle = HarkType.body.copy(fontSize = 14.sp),
                )
                MetaLabel("Default: ${AiSettings.DEFAULT_BASE_URL}", color = c.inkFaint)
            }

            // Model field
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("MODEL")
                OutlinedTextField(
                    value = model,
                    onValueChange = {
                        model = it
                        savedFeedback = false
                    },
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
                MetaLabel("Default: ${AiSettings.DEFAULT_MODEL}", color = c.inkFaint)
            }

            if (savedFeedback) {
                Text(
                    "Settings saved successfully.",
                    style = HarkType.secondary,
                    color = c.rust,
                )
            }

            Spacer(Modifier.height(8.dp))
        }

        // Save Button for AI Config
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(c.ink)
                    .clickable {
                        vm.save(
                            currentSettings.copy(
                                apiKey = apiKey.trim(),
                                baseUrl = baseUrl.trim().ifBlank { AiSettings.DEFAULT_BASE_URL },
                                model = model.trim().ifBlank { AiSettings.DEFAULT_MODEL },
                            )
                        )
                        savedFeedback = true
                        if (sync.isEnabled) {
                            scope.launch {
                                try {
                                    sync.pushSettings()
                                } catch (e: Exception) {
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("SAVE AI SETTINGS", style = HarkType.label, color = c.paper)
            }
        }
    }
}

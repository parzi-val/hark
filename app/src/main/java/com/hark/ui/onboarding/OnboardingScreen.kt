package com.hark.ui.onboarding

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hark.HarkApp
import com.hark.sync.GoogleAuth
import com.hark.ui.components.HarkMark
import com.hark.ui.components.MetaLabel
import com.hark.ui.components.SectionLabel
import com.hark.ui.theme.Hark
import com.hark.ui.theme.HarkType
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as HarkApp
    val store = app.container.settingsStore
    val sync = app.container.syncManager
    val settings by store.settings.collectAsStateWithLifecycle()
    val c = Hark.colors
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(pageCount = { 3 })
    var name by remember { mutableStateOf(settings.userName) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var signedIn by remember { mutableStateOf(sync.isEnabled) }
    var syncBusy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            scope.launch {
                try {
                    val account = GoogleAuth.resultFromIntent(data)
                    if (account != null) {
                        signedIn = true
                        errorMsg = null
                        // App-scoped so tapping Enter Hark before it finishes can't cancel the first sync.
                        sync.onSignedInAsync()
                    } else {
                        errorMsg = GoogleAuth.lastError ?: "Sign-in failed"
                    }
                } catch (e: Exception) {
                    errorMsg = GoogleAuth.lastError ?: "Sync failed: ${e.message}"
                } finally {
                    syncBusy = false
                }
            }
        } else {
            syncBusy = false
            errorMsg = GoogleAuth.lastError ?: "Sign-in cancelled"
        }
    }

    val startSignIn: () -> Unit = {
        syncBusy = true
        signInLauncher.launch(GoogleAuth.getSignInIntent(ctx))
    }

    Surface(modifier = Modifier.fillMaxSize(), color = c.paper) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top Bar with Skip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetaLabel("HARK · WELCOME", color = c.inkFaint)
                if (pagerState.currentPage < 2) {
                    MetaLabel(
                        "SKIP",
                        color = c.inkMuted,
                        modifier = Modifier.clickable {
                            scope.launch { pagerState.animateScrollToPage(2) }
                        },
                    )
                }
            }

            // Pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                when (page) {
                    0 -> PageCapture()
                    1 -> PageStreamAndShelf()
                    2 -> PageSetup(
                        name = name,
                        onNameChange = { name = it },
                        apiKey = apiKey,
                        onApiKeyChange = { apiKey = it },
                        signedIn = signedIn,
                        syncBusy = syncBusy,
                        onSignIn = startSignIn,
                        error = errorMsg,
                    )
                }
            }

            // Bottom Navigation & Progress Dots
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Dots indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(3) { index ->
                        val isCurrent = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .size(if (isCurrent) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(if (isCurrent) c.rust else c.inkHairline),
                        )
                    }
                }

                // Action CTA
                if (pagerState.currentPage < 2) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(c.ink)
                            .clickable {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("CONTINUE", style = HarkType.label, color = c.paper)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(c.ink)
                            .clickable {
                                store.setUserName(name)
                                if (apiKey.isNotBlank()) {
                                    store.update(settings.copy(userName = name.trim(), apiKey = apiKey.trim(), hasCompletedOnboarding = true))
                                } else {
                                    store.setOnboardingCompleted(true)
                                }
                                if (sync.isEnabled) {
                                    sync.pushSettingsAsync()
                                }
                                onFinish()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("ENTER HARK", style = HarkType.label, color = c.paper)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageCapture() {
    val c = Hark.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HarkMark(
            modifier = Modifier.size(110.dp),
            progress = 1f,
            color = c.rust,
        )
        Spacer(Modifier.height(32.dp))
        Text(
            "Speak your mind.\nWe'll shape the thought.",
            style = HarkType.noteTitle.copy(fontSize = 26.sp, lineHeight = 34.sp),
            color = c.ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Hark tidies raw voice thoughts into structured Markdown and extracts action items with zero friction.",
            style = HarkType.secondary,
            color = c.inkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun PageStreamAndShelf() {
    val c = Hark.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.paperRaised)
                    .border(1.dp, c.inkHairline, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("THE STREAM", style = HarkType.label, color = c.ink)
            }
            Text("→", style = HarkType.noteTitle, color = c.rust)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.paperRaised)
                    .border(1.dp, c.inkHairline, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text("THE SHELF", style = HarkType.label, color = c.rust)
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            "The River & The Shelf",
            style = HarkType.noteTitle.copy(fontSize = 24.sp),
            color = c.ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Quick notes flow through the daily Stream. Deep essays and reading rest in The Shelf.\n\nEvery day also brings one elevated word of vocabulary (λέξις) to expand your expression.",
            style = HarkType.secondary,
            color = c.inkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun PageSetup(
    name: String,
    onNameChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    signedIn: Boolean,
    syncBusy: Boolean,
    onSignIn: () -> Unit,
    error: String? = null,
) {
    val c = Hark.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Make it yours.",
                style = HarkType.noteTitle.copy(fontSize = 26.sp),
                color = c.ink,
            )
            Text(
                "Personalize Hark and connect optional cloud sync.",
                style = HarkType.secondary,
                color = c.inkMuted,
            )
        }

        // Name input
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel("YOUR NAME")
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                placeholder = { Text("e.g. Bala", style = HarkType.secondary, color = c.inkFaint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.ink,
                    unfocusedBorderColor = c.checkboxBorder,
                    focusedTextColor = c.ink,
                    unfocusedTextColor = c.ink,
                ),
                textStyle = HarkType.secondary,
            )
        }

        // Optional Google Drive Sync
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel("GOOGLE DRIVE SYNC (OPTIONAL)")
            if (!signedIn) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(c.paperRaised)
                        .border(1.dp, c.inkHairline, RoundedCornerShape(23.dp))
                        .clickable(enabled = !syncBusy) { onSignIn() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (syncBusy) "CONNECTING…" else "CONNECT GOOGLE DRIVE",
                        style = HarkType.label,
                        color = c.ink,
                    )
                }
                if (error != null) {
                    Text(error, style = HarkType.meta, color = c.rust)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.paperRaised)
                        .border(1.dp, c.inkHairline, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("✓ Drive connected", style = HarkType.item, color = c.rust)
                }
            }
        }

        // Optional AI Key
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel("GROQ / OPENAI API KEY (OPTIONAL)")
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                placeholder = { Text("gsk_... or sk-...", style = HarkType.secondary, color = c.inkFaint) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.ink,
                    unfocusedBorderColor = c.checkboxBorder,
                    focusedTextColor = c.ink,
                    unfocusedTextColor = c.ink,
                ),
                textStyle = HarkType.secondary,
            )
            Text(
                "You can also add or change your API key anytime later in Settings.",
                style = HarkType.meta,
                color = c.inkFaint,
            )
        }
    }
}

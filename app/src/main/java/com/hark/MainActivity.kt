package com.hark

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hark.ui.compose.ComposeScreen
import com.hark.ui.note.NoteDetailScreen
import com.hark.ui.recall.RecallScreen
import com.hark.ui.settings.SettingsScreen
import com.hark.ui.shelf.ShelfScreen
import com.hark.ui.splash.SplashScreen
import com.hark.ui.stream.StreamScreen
import com.hark.ui.talk.TalkScreen
import com.hark.ui.theme.Hark
import com.hark.ui.theme.HarkTheme
import com.hark.ui.theme.HarkType
import com.hark.ui.today.TodayScreen

sealed interface Screen {
    data object Splash : Screen

    /** Primary destinations that share the bottom nav. */
    sealed interface Tab : Screen
    data object Stream : Tab
    data object Today : Tab
    data object Recall : Tab
    data object Settings : Tab

    // Pushed full-screen (no bottom nav).
    data object Shelf : Screen
    data class Talk(val focusedNoteId: Long? = null) : Screen
    data object Compose : Screen
    data class NoteDetail(val noteId: Long) : Screen
}

class MainActivity : ComponentActivity() {

    private val backStack = mutableStateListOf<Screen>(Screen.Splash)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            val app = applicationContext as HarkApp
            val settings by app.container.settingsStore.settings.collectAsStateWithLifecycle()
            val isDark = when (settings.themeMode) {
                com.hark.ai.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                com.hark.ai.ThemeMode.LIGHT -> false
                com.hark.ai.ThemeMode.DARK -> true
            }

            val currentScreen = backStack.lastOrNull() ?: Screen.Stream

            HarkTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().systemBarsPadding()) {
                        AppNavigation(
                            currentScreen = currentScreen,
                            onNavigate = { screen ->
                                if (screen is Screen.Tab) {
                                    backStack.clear()
                                    backStack.add(screen)
                                } else if (screen is Screen.Splash) {
                                    backStack.clear()
                                    backStack.add(Screen.Splash)
                                } else {
                                    if (backStack.size == 1 && backStack.first() == Screen.Splash) {
                                        backStack.clear()
                                        backStack.add(screen)
                                    } else {
                                        backStack.add(screen)
                                    }
                                }
                            },
                            onBack = {
                                if (backStack.size > 1) {
                                    backStack.removeAt(backStack.lastIndex)
                                } else {
                                    val only = backStack.firstOrNull()
                                    if (only != null && only != Screen.Stream && only != Screen.Splash) {
                                        backStack.clear()
                                        backStack.add(Screen.Stream)
                                    } else {
                                        finish()
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_TALK || intent?.getBooleanExtra(EXTRA_START_TALK, false) == true) {
            backStack.add(Screen.Talk())
        }
    }

    companion object {
        const val ACTION_TALK = "com.hark.action.TALK"
        const val EXTRA_START_TALK = "extra_start_talk"
    }
}

@Composable
private fun AppNavigation(currentScreen: Screen, onNavigate: (Screen) -> Unit, onBack: () -> Unit) {
    BackHandler(enabled = currentScreen != Screen.Stream && currentScreen != Screen.Splash) { onBack() }

    when (currentScreen) {
        Screen.Splash -> SplashScreen(onFinished = { onNavigate(Screen.Stream) })

        is Screen.Tab -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().weight(1f)) {
                when (currentScreen) {
                    Screen.Stream -> StreamScreen(
                        onTalk = { onNavigate(Screen.Talk()) },
                        onWrite = { onNavigate(Screen.Compose) },
                        onOpenSettings = { onNavigate(Screen.Settings) },
                        onOpenNote = { onNavigate(Screen.NoteDetail(it)) },
                        onToggleShelf = { onNavigate(Screen.Shelf) },
                    )
                    Screen.Today -> TodayScreen(onOpenNote = { onNavigate(Screen.NoteDetail(it)) })
                    Screen.Recall -> RecallScreen(onOpenNote = { onNavigate(Screen.NoteDetail(it)) })
                    Screen.Settings -> SettingsScreen(onClose = onBack)
                }
            }
            BottomNav(current = currentScreen, onTab = onNavigate)
        }

        Screen.Shelf -> ShelfScreen(
            onOpenNote = { onNavigate(Screen.NoteDetail(it)) },
            onToggleStream = onBack,
        )

        is Screen.Talk -> TalkScreen(
            onClose = onBack,
            onKept = { noteId ->
                onBack()
                onNavigate(Screen.NoteDetail(noteId))
            },
            focusedNoteId = currentScreen.focusedNoteId,
        )
        Screen.Compose -> ComposeScreen(onClose = onBack, onSaved = onBack)
        is Screen.NoteDetail -> NoteDetailScreen(
            noteId = currentScreen.noteId,
            onClose = onBack,
            onTalkToEdit = { onNavigate(Screen.Talk(focusedNoteId = currentScreen.noteId)) },
        )
    }
}

@Composable
private fun BottomNav(current: Screen.Tab, onTab: (Screen) -> Unit) {
    val c = Hark.colors
    Column {
        HorizontalDivider(color = c.inkHairline)
        Row(
            Modifier.fillMaxWidth().background(c.paper).padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            NavItem("STREAM", Screen.Stream, current, onTab)
            NavItem("TODAY", Screen.Today, current, onTab)
            NavItem("RECALL", Screen.Recall, current, onTab)
            NavItem("SETTINGS", Screen.Settings, current, onTab)
        }
    }
}

@Composable
private fun NavItem(label: String, tab: Screen.Tab, current: Screen.Tab, onTab: (Screen) -> Unit) {
    val c = Hark.colors
    Text(
        label,
        style = HarkType.label,
        color = if (tab == current) c.ink else c.inkFaint,
        modifier = Modifier.clickable { onTab(tab) },
    )
}

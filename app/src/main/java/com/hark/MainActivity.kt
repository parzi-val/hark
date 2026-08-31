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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.hark.ui.components.HilbertSpinner
import com.hark.ui.compose.ComposeScreen
import com.hark.ui.lexicon.LexiconScreen
import com.hark.ui.lexicon.LexiconWordScreen
import com.hark.ui.note.NoteDetailScreen
import com.hark.ui.onboarding.OnboardingScreen
import com.hark.ui.search.SearchScreen
import com.hark.ui.settings.SettingsScreen
import com.hark.ui.shelf.ShelfScreen
import com.hark.ui.splash.SplashScreen
import com.hark.ui.stream.StreamScreen
import com.hark.ui.talk.TalkScreen
import com.hark.ui.theme.Hark
import com.hark.ui.theme.HarkTheme
import com.hark.ui.theme.HarkType
import com.hark.ui.today.TodayScreen
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Splash : Screen
    data object Onboarding : Screen

    /** Primary destinations that share the bottom nav. */
    sealed interface Tab : Screen
    data object Stream : Tab
    data object Today : Tab
    data object Shelf : Tab
    data object Settings : Tab

    // Pushed full-screen (no bottom nav).
    data class Talk(val focusedNoteId: Long? = null) : Screen
    data object Compose : Screen
    data object Search : Screen
    data class NoteDetail(val noteId: Long) : Screen
    data object Lexicon : Screen
    data class LexiconEntry(val wordId: String) : Screen
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
            val initialSyncing by app.container.syncManager.initialSyncing.collectAsStateWithLifecycle()
            val isDark = when (settings.themeMode) {
                com.hark.ai.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                com.hark.ai.ThemeMode.LIGHT -> false
                com.hark.ai.ThemeMode.DARK -> true
            }

            val currentScreen = backStack.lastOrNull() ?: Screen.Stream

            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                var pollingJob: kotlinx.coroutines.Job? = null
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        // Always start the poll; runForegroundPolling syncs immediately then every
                        // 3s and checks isEnabled itself — so mid-session sign-in works with no relaunch.
                        pollingJob?.cancel()
                        pollingJob = (lifecycleOwner as androidx.lifecycle.LifecycleOwner).lifecycleScope.launch {
                            app.container.syncManager.runForegroundPolling()
                        }
                    } else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                        pollingJob?.cancel()
                        pollingJob = null
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    pollingJob?.cancel()
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            HarkTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().systemBarsPadding()) {
                        AppNavigation(
                            currentScreen = currentScreen,
                            hasCompletedOnboarding = settings.hasCompletedOnboarding,
                            onNavigate = { screen ->
                                if (screen is Screen.Tab) {
                                    backStack.clear()
                                    backStack.add(screen)
                                } else if (screen is Screen.Splash || screen is Screen.Onboarding) {
                                    backStack.clear()
                                    backStack.add(screen)
                                } else {
                                    if (backStack.size == 1 && (backStack.first() == Screen.Splash || backStack.first() == Screen.Onboarding)) {
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
                                    if (only != null && only != Screen.Stream && only != Screen.Splash && only != Screen.Onboarding) {
                                        backStack.clear()
                                        backStack.add(Screen.Stream)
                                    } else {
                                        finish()
                                    }
                                }
                            },
                        )

                        // Entry sync loader: cover the app with the Hilbert spinner until the
                        // first post-sign-in sync finishes, so we don't flash the starter note.
                        val c = Hark.colors
                        val inApp = currentScreen != Screen.Splash && currentScreen != Screen.Onboarding
                        if (initialSyncing && inApp) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(c.paper),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(20.dp),
                                ) {
                                    HilbertSpinner(color = c.rust)
                                    Text(
                                        "Syncing your notes…",
                                        style = HarkType.label,
                                        color = c.inkMuted,
                                    )
                                }
                            }
                        }
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
        if (intent == null) return
        if (intent.action == ACTION_TALK || intent.getBooleanExtra(EXTRA_START_TALK, false)) {
            backStack.clear()
            backStack.add(Screen.Stream)
            backStack.add(Screen.Talk())
        }
    }

    companion object {
        const val ACTION_TALK = "com.hark.action.TALK"
        const val EXTRA_START_TALK = "extra_start_talk"
    }
}

@Composable
private fun AppNavigation(
    currentScreen: Screen,
    hasCompletedOnboarding: Boolean,
    onNavigate: (Screen) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(enabled = currentScreen != Screen.Stream && currentScreen != Screen.Splash && currentScreen != Screen.Onboarding) { onBack() }

    when (currentScreen) {
        Screen.Splash -> SplashScreen(onFinished = {
            onNavigate(if (hasCompletedOnboarding) Screen.Stream else Screen.Onboarding)
        })

        Screen.Onboarding -> OnboardingScreen(onFinish = {
            onNavigate(Screen.Stream)
        })

        is Screen.Tab -> HomePager(currentTab = currentScreen, onNavigate = onNavigate)

        is Screen.Talk -> TalkScreen(
            onClose = onBack,
            onKept = { noteId ->
                onBack()
                onNavigate(Screen.NoteDetail(noteId))
            },
            focusedNoteId = currentScreen.focusedNoteId,
        )
        Screen.Compose -> ComposeScreen(onClose = onBack, onSaved = onBack)
        Screen.Search -> SearchScreen(onClose = onBack, onOpenNote = { onNavigate(Screen.NoteDetail(it)) })
        is Screen.NoteDetail -> NoteDetailScreen(
            noteId = currentScreen.noteId,
            onClose = onBack,
            onTalkToEdit = { onNavigate(Screen.Talk(focusedNoteId = currentScreen.noteId)) },
        )
        Screen.Lexicon -> LexiconScreen(
            onClose = onBack,
            onOpenWord = { onNavigate(Screen.LexiconEntry(it)) },
        )
        is Screen.LexiconEntry -> LexiconWordScreen(
            wordId = currentScreen.wordId,
            onClose = onBack,
            onOpenArchive = { onNavigate(Screen.Lexicon) },
        )
    }
}

/**
 * The four bottom-nav destinations as one horizontally-swipeable pager: Stream · Today · Shelf ·
 * Settings. Swiping moves between them; the bottom nav highlights the current page and tapping a
 * tab animates the pager to it (kept in sync both ways via the backStack's current tab).
 */
@Composable
private fun HomePager(currentTab: Screen.Tab, onNavigate: (Screen) -> Unit) {
    val tabs = remember { listOf(Screen.Stream, Screen.Today, Screen.Shelf, Screen.Settings) }
    val pagerState = rememberPagerState(
        initialPage = tabs.indexOf(currentTab).coerceAtLeast(0),
        pageCount = { tabs.size },
    )
    val scope = rememberCoroutineScope()

    // Nav tap updates the backStack (currentTab) → animate the pager to it.
    LaunchedEffect(currentTab) {
        val idx = tabs.indexOf(currentTab)
        if (idx in tabs.indices && idx != pagerState.currentPage) pagerState.animateScrollToPage(idx)
    }
    // Swipe settles on a page → make it the current tab (highlights nav, syncs the backStack).
    LaunchedEffect(pagerState.settledPage) {
        tabs.getOrNull(pagerState.settledPage)?.let { if (it != currentTab) onNavigate(it) }
    }

    Column(Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
            when (tabs[page]) {
                Screen.Stream -> StreamScreen(
                    onTalk = { onNavigate(Screen.Talk()) },
                    onWrite = { onNavigate(Screen.Compose) },
                    onOpenSettings = { scope.launch { pagerState.animateScrollToPage(3) } },
                    onOpenNote = { onNavigate(Screen.NoteDetail(it)) },
                    onToggleShelf = { scope.launch { pagerState.animateScrollToPage(2) } },
                    onOpenSearch = { onNavigate(Screen.Search) },
                )
                Screen.Today -> TodayScreen(
                    onOpenNote = { onNavigate(Screen.NoteDetail(it)) },
                    onOpenLexicon = { onNavigate(Screen.Lexicon) },
                    onOpenWord = { onNavigate(Screen.LexiconEntry(it)) },
                )
                Screen.Shelf -> ShelfScreen(
                    onOpenNote = { onNavigate(Screen.NoteDetail(it)) },
                    onToggleStream = { scope.launch { pagerState.animateScrollToPage(0) } },
                )
                Screen.Settings -> SettingsScreen(onClose = { scope.launch { pagerState.animateScrollToPage(0) } })
                else -> Unit
            }
        }
        BottomNav(current = tabs[pagerState.currentPage], onTab = onNavigate)
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
            NavItem("Stream", Screen.Stream, current, onTab)
            NavItem("Today", Screen.Today, current, onTab)
            NavItem("Shelf", Screen.Shelf, current, onTab)
            NavItem("Settings", Screen.Settings, current, onTab)
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

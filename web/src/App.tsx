import { useState, useEffect, useSyncExternalStore } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db, seedStarterIfEmpty, TaskEntity, SettingsEntity, DEFAULT_BASE_URL, DEFAULT_MODEL } from './db/db';
import { FocusedNote } from './ai/groq';
import { focusedNoteOf, newShelfNote, setTaskStatus, archiveNote, unarchiveNote } from './db/actions';
import { syncNow, isSyncEnabled, scheduleSync, firstSyncAsync, subscribeInitialSyncing, getInitialSyncing } from './sync/sync';
import { HilbertLoader } from './components/HilbertLoader';
import { Header, FilterTab } from './components/Header';
import { StreamView } from './components/StreamView';
import { GridView } from './components/GridView';
import { ShelfView } from './components/ShelfView';
import { KanbanBoard } from './components/KanbanBoard';
import { ArchiveView } from './components/ArchiveView';
import { NoteDetail } from './components/NoteDetail';
import { TalkModal } from './components/TalkModal';
import { ComposeModal } from './components/ComposeModal';
import { EditTaskDialog } from './components/EditTaskDialog';
import { RecallModal } from './components/RecallModal';
import { SettingsModal } from './components/SettingsModal';
import { FloatingCapture } from './components/FloatingCapture';
import { SplashScreen } from './components/SplashScreen';
import { LandingPage } from './components/LandingPage';
import { OnboardingModal } from './components/OnboardingModal';

function getInitialRoute(): 'landing' | 'home' {
  const path = window.location.pathname.toLowerCase().replace(/\/+$/, '');
  const hash = window.location.hash.toLowerCase();
  if (path === '/home' || hash === '#/home' || hash === '#home') {
    return 'home';
  }
  return 'landing';
}

export function App() {
  const [route, setRoute] = useState<'landing' | 'home'>(getInitialRoute);
  const [showSplash, setShowSplash] = useState(() => {
    return !sessionStorage.getItem('hark_splash_shown');
  });
  const [filter, setFilter] = useState<FilterTab>('ALL');
  const [selectedNoteId, setSelectedNoteId] = useState<number | null>(null);
  const [editingTask, setEditingTask] = useState<TaskEntity | null>(null);
  const [showTalk, setShowTalk] = useState(false);
  const [showCompose, setShowCompose] = useState(false);
  const [showRecall, setShowRecall] = useState(false);
  const [showSettings, setShowSettings] = useState(false);
  const [talkFocus, setTalkFocus] = useState<FocusedNote | null>(null);
  const [view, setView] = useState<'stream' | 'shelf'>('stream');

  useEffect(() => {
    const handlePopState = () => {
      setRoute(getInitialRoute());
    };
    window.addEventListener('popstate', handlePopState);
    window.addEventListener('hashchange', handlePopState);
    return () => {
      window.removeEventListener('popstate', handlePopState);
      window.removeEventListener('hashchange', handlePopState);
    };
  }, []);

  const navigateTo = (newRoute: 'landing' | 'home') => {
    setRoute(newRoute);
    const targetPath = newRoute === 'home' ? '/home' : '/';
    if (window.location.pathname !== targetPath) {
      window.history.pushState({}, '', targetPath);
    }
  };

  // Initialize DB seed
  useEffect(() => {
    seedStarterIfEmpty();

    // Check URL actions for iOS Shortcuts (?action=talk / ?action=write)
    const params = new URLSearchParams(window.location.search);
    if (params.get('action') === 'talk') {
      setShowTalk(true);
    } else if (params.get('action') === 'write') {
      setShowCompose(true);
    }
  }, []);

  // Sync on open, every 3s while visible, and whenever the tab regains focus. The interval is
  // always installed and re-checks isSyncEnabled() per tick — so signing in mid-session starts
  // syncing without a reload, and signing out stops it. syncNow() is self-guarded against overlap.
  useEffect(() => {
    if (isSyncEnabled()) firstSyncAsync(); // initial sync WITH the entry loader
    const run = () => {
      if (isSyncEnabled() && document.visibilityState === 'visible') void syncNow().catch(() => {});
    };
    const interval = setInterval(run, 3000);
    document.addEventListener('visibilitychange', run);
    return () => {
      clearInterval(interval);
      document.removeEventListener('visibilitychange', run);
    };
  }, []);

  // Reactive DB queries. `deleted` is stored as a boolean, which IndexedDB can't index
  // (false !== 0), so filter in JS rather than via .where('deleted').equals(0). Views sort/group.
  const notes = useLiveQuery(() => db.notes.filter((n) => !n.deleted).toArray(), []) || [];
  const tasks = useLiveQuery(() => db.tasks.filter((t) => !t.deleted).toArray(), []) || [];
  const rawSettings = useLiveQuery(() => db.settings.get(1), []);
  const initialSyncing = useSyncExternalStore(subscribeInitialSyncing, getInitialSyncing);

  // Hold the loader over the first paint until IndexedDB settles — otherwise the app renders one
  // frame with fallback settings (empty key → "configure" badge) + no notes: the flash. Safety
  // timeout so a missing settings row can never hang the loader.
  const [dataReady, setDataReady] = useState(false);
  useEffect(() => {
    if (rawSettings !== undefined) {
      setDataReady(true);
      return;
    }
    const t = setTimeout(() => setDataReady(true), 2000);
    return () => clearTimeout(t);
  }, [rawSettings]);

  const settings: SettingsEntity = rawSettings || {
    id: 1,
    apiKey: '',
    baseUrl: DEFAULT_BASE_URL,
    model: DEFAULT_MODEL,
    themeMode: 'LIGHT',
    viewMode: 'STREAM',
    hasCompletedOnboarding: false,
  };

  // ponytail: derived open/deferred counts for header
  const openCount = tasks.filter((t) => !t.done && !t.deferred).length;
  const deferredCount = tasks.filter((t) => !t.done && t.deferred).length;

  // Stream vs Shelf vs Archive split.
  const streamNotes = notes.filter((n) => !n.shelf && !n.archived);
  const shelfNotes = notes.filter((n) => n.shelf && !n.archived);
  const archivedNotes = notes.filter((n) => n.archived);
  const selectedNote = notes.find((n) => n.id === selectedNoteId) || null;
  const focusMode = !!selectedNote?.shelf; // shelf notes open full-screen

  // iOS App Icon Badge API
  useEffect(() => {
    if ('setAppBadge' in navigator) {
      if (openCount > 0) {
        (navigator as any).setAppBadge(openCount).catch(() => {});
      } else {
        (navigator as any).clearAppBadge().catch(() => {});
      }
    }
  }, [openCount]);

  // Theme Sync
  useEffect(() => {
    const root = document.documentElement;
    const isDark =
      settings.themeMode === 'DARK' ||
      (settings.themeMode === 'SYSTEM' && window.matchMedia('(prefers-color-scheme: dark)').matches);

    if (isDark) {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }
  }, [settings.themeMode]);

  // Keyboard Shortcuts (Cmd+N, Cmd+K, T, Esc)
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      const isInput =
        document.activeElement?.tagName === 'INPUT' ||
        document.activeElement?.tagName === 'TEXTAREA';

      if (e.key === 'Escape') {
        setShowTalk(false);
        setShowCompose(false);
        setShowRecall(false);
        setShowSettings(false);
        setEditingTask(null);
        setSelectedNoteId(null);
      } else if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        setShowRecall(true);
      } else if ((e.metaKey || e.ctrlKey) && e.key === 'n') {
        e.preventDefault();
        setShowCompose(true);
      } else if (e.key === 't' && !isInput && !e.metaKey && !e.ctrlKey) {
        e.preventDefault();
        setShowTalk(true);
      } else if (e.code === 'Space' && !isInput && !e.metaKey && !e.ctrlKey && !e.repeat) {
        // Hold SPACE = push-to-talk; TalkModal finishes on release (keyup).
        e.preventDefault();
        setShowTalk(true);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  const handleToggleTask = async (taskId: number, currentDone: boolean) => {
    await setTaskStatus(taskId, currentDone ? 'OPEN' : 'COMPLETED');
  };

  const handleToggleViewMode = async () => {
    const nextMode = settings.viewMode === 'STREAM' ? 'GRID' : 'STREAM';
    await db.settings.update(1, { viewMode: nextMode });
  };

  const handleDeleteNote = async (id: number) => {
    setSelectedNoteId(null);
    const now = Date.now();
    await db.notes.update(id, { deleted: true, updatedAt: now });
    await db.tasks.where('sourceNoteId').equals(id).modify({ deleted: true, updatedAt: now });
    scheduleSync(0);
  };

  const handleArchiveNote = async (id: number) => {
    setSelectedNoteId(null);
    await archiveNote(id);
  };

  const openTalkToEdit = async (noteId: number) => {
    setTalkFocus(await focusedNoteOf(noteId));
    setShowTalk(true);
  };

  const handleNewShelfNote = async () => {
    const id = await newShelfNote();
    setView('shelf');
    setSelectedNoteId(id);
  };

  // If user is on the landing page (route === 'landing'), render the marketing/intro home page
  if (route === 'landing') {
    return (
      <LandingPage
        onEnterApp={() => {
          localStorage.setItem('hark_entered_app', '1');
          navigateTo('home');
        }}
      />
    );
  }

  return (
    <div className="min-h-screen bg-paper flex flex-col text-ink selection:bg-rust selection:text-white relative">
      {/* Entry sync loader — after onboarding, hold the Hilbert trace until the first sync lands. */}
      {!showSplash && (!dataReady || initialSyncing) && (
        <div className="fixed inset-0 z-[100] bg-paper flex flex-col items-center justify-center gap-5">
          <HilbertLoader className="w-16 h-16 text-rust" />
          <span className="font-mono text-xs text-ink-muted font-medium">Syncing your notes…</span>
        </div>
      )}

      {/* Full-screen Shelf Note (Long-form writer focus mode) */}
      {selectedNoteId && focusMode && (
        <div className="fixed inset-0 z-50 bg-paper animate-fade-in">
          <NoteDetail
            noteId={selectedNoteId}
            settings={settings}
            onClose={() => setSelectedNoteId(null)}
            onDeleteNote={handleDeleteNote}
            onArchiveNote={handleArchiveNote}
            onTalkToEdit={() => openTalkToEdit(selectedNoteId)}
          />
        </div>
      )}

      {/* Overlay Side-Drawer for Stream Notes (occupies ~1/3 space with dropshadow, workspace does not resize) */}
      {selectedNoteId && !focusMode && (
        <>
          {/* Subtle backdrop */}
          <div
            onClick={() => setSelectedNoteId(null)}
            className="fixed inset-0 z-40 bg-ink/15 backdrop-blur-[1px] animate-fade-in"
          />

          {/* Sidedrawer pane */}
          <div className="fixed inset-y-0 right-0 z-50 w-full sm:w-[480px] lg:w-[35%] xl:w-[32%] bg-paper shadow-2xl border-l border-ink-hairline animate-slide-in-right flex flex-col">
            <NoteDetail
              noteId={selectedNoteId}
              settings={settings}
              onClose={() => setSelectedNoteId(null)}
              onDeleteNote={handleDeleteNote}
              onArchiveNote={handleArchiveNote}
              onTalkToEdit={() => openTalkToEdit(selectedNoteId)}
            />
          </div>
        </>
      )}

      {/* Main Full-Width Canvas (stays full size without resizing/jumping) */}
      <div className="flex-1 flex flex-col w-full">
        <Header
          openCount={openCount}
          deferredCount={deferredCount}
          activeFilter={filter}
          onFilterChange={setFilter}
          viewMode={settings.viewMode}
          onToggleViewMode={handleToggleViewMode}
          onOpenRecall={() => setShowRecall(true)}
          onOpenSettings={() => setShowSettings(true)}
          isConfigured={!!settings.apiKey}
          view={view}
          onToggleShelf={() => setView((v) => (v === 'shelf' ? 'stream' : 'shelf'))}
          userName={settings.userName}
          onNavigateLanding={() => navigateTo('landing')}
        />

        <main className="flex-1 overflow-y-auto pb-28">
          {view === 'shelf' ? (
            <ShelfView
              notes={shelfNotes}
              onOpenNote={(id) => setSelectedNoteId(id)}
              onNewShelfNote={handleNewShelfNote}
            />
          ) : filter === 'OPEN' ? (
            <KanbanBoard
              notes={streamNotes}
              tasks={tasks}
              onOpenNote={(id) => setSelectedNoteId(id)}
              onEditTask={(task) => setEditingTask(task)}
            />
          ) : filter === 'ARCHIVE' ? (
            <ArchiveView
              notes={archivedNotes}
              tasks={tasks}
              onOpenNote={(id) => setSelectedNoteId(id)}
              onUnarchive={unarchiveNote}
            />
          ) : settings.viewMode === 'STREAM' ? (
            <StreamView
              notes={streamNotes}
              tasks={tasks}
              filter={filter}
              onToggleTask={handleToggleTask}
              onOpenNote={(id) => setSelectedNoteId(id)}
              onEditTask={(task) => setEditingTask(task)}
            />
          ) : (
            <GridView
              notes={streamNotes}
              tasks={tasks}
              filter={filter}
              onToggleTask={handleToggleTask}
              onOpenNote={(id) => setSelectedNoteId(id)}
              onEditTask={(task) => setEditingTask(task)}
            />
          )}
        </main>
      </div>

      {/* Floating Action Button (Collapsed into pencil FAB, expands on tap) */}
      <FloatingCapture
        onWrite={() => setShowCompose(true)}
        onTalk={() => { setTalkFocus(null); setShowTalk(true); }}
        onShelf={handleNewShelfNote}
      />

      {/* Modals & Dialogs */}
      {showTalk && (
        <TalkModal
          settings={settings}
          focusedNote={talkFocus}
          onClose={() => { setShowTalk(false); setTalkFocus(null); }}
          onSaved={(id) => {
            setShowTalk(false);
            setTalkFocus(null);
            setSelectedNoteId(id);
          }}
        />
      )}

      {showCompose && (
        <ComposeModal
          settings={settings}
          onClose={() => setShowCompose(false)}
          onSaved={(id) => {
            setShowCompose(false);
            if (typeof id === 'number') setSelectedNoteId(id);
          }}
        />
      )}

      {editingTask && (
        <EditTaskDialog
          task={editingTask}
          onClose={() => setEditingTask(null)}
          onUpdated={() => setEditingTask(null)}
        />
      )}

      {showRecall && (
        <RecallModal
          settings={settings}
          onClose={() => setShowRecall(false)}
        />
      )}

      {showSettings && (
        <SettingsModal
          settings={settings}
          onClose={() => setShowSettings(false)}
          onSaved={() => {}}
        />
      )}

      {/* Onboarding modal for fresh users */}
      {!showSplash && rawSettings && !rawSettings.hasCompletedOnboarding && (
        <OnboardingModal
          settings={settings}
          onFinish={() => {}}
        />
      )}

      {/* Hilbert curve splash on initial load */}
      {showSplash && (
        <SplashScreen
          onFinished={() => {
            sessionStorage.setItem('hark_splash_shown', 'true');
            setShowSplash(false);
          }}
        />
      )}
    </div>
  );
}

export default App;

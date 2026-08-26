import { useState, useEffect } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { db, seedStarterIfEmpty, TaskEntity, SettingsEntity, DEFAULT_BASE_URL, DEFAULT_MODEL } from './db/db';
import { FocusedNote } from './ai/groq';
import { focusedNoteOf, newShelfNote } from './db/actions';
import { Header } from './components/Header';
import { StreamView } from './components/StreamView';
import { GridView } from './components/GridView';
import { ShelfView } from './components/ShelfView';
import { NoteDetail } from './components/NoteDetail';
import { TalkModal } from './components/TalkModal';
import { ComposeModal } from './components/ComposeModal';
import { EditTaskDialog } from './components/EditTaskDialog';
import { RecallModal } from './components/RecallModal';
import { SettingsModal } from './components/SettingsModal';
import { FloatingCapture } from './components/FloatingCapture';
import { SplashScreen } from './components/SplashScreen';

export function App() {
  const [showSplash, setShowSplash] = useState(() => {
    return !sessionStorage.getItem('hark_splash_shown');
  });
  const [filter, setFilter] = useState<'ALL' | 'OPEN' | 'NOTES'>('ALL');
  const [selectedNoteId, setSelectedNoteId] = useState<number | null>(null);
  const [editingTask, setEditingTask] = useState<TaskEntity | null>(null);
  const [showTalk, setShowTalk] = useState(false);
  const [showCompose, setShowCompose] = useState(false);
  const [showRecall, setShowRecall] = useState(false);
  const [showSettings, setShowSettings] = useState(false);
  const [talkFocus, setTalkFocus] = useState<FocusedNote | null>(null);
  const [view, setView] = useState<'stream' | 'shelf'>('stream');

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

  // Reactive DB queries. `deleted` is stored as a boolean, which IndexedDB can't index
  // (false !== 0), so filter in JS rather than via .where('deleted').equals(0). Views sort/group.
  const notes = useLiveQuery(() => db.notes.filter((n) => !n.deleted).toArray(), []) || [];
  const tasks = useLiveQuery(() => db.tasks.filter((t) => !t.deleted).toArray(), []) || [];
  const rawSettings = useLiveQuery(() => db.settings.get(1), []);

  const settings: SettingsEntity = rawSettings || {
    id: 1,
    apiKey: '',
    baseUrl: DEFAULT_BASE_URL,
    model: DEFAULT_MODEL,
    themeMode: 'SYSTEM',
    viewMode: 'STREAM',
  };

  const openCount = tasks.filter((t) => !t.done).length;

  // Stream vs Shelf split.
  const shelfNotes = notes.filter((n) => n.shelf);
  const streamNotes = notes.filter((n) => !n.shelf);
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
    await db.tasks.update(taskId, {
      done: !currentDone,
      doneAt: !currentDone ? Date.now() : null,
      updatedAt: Date.now(),
    });
  };

  const handleToggleViewMode = async () => {
    const nextMode = settings.viewMode === 'STREAM' ? 'GRID' : 'STREAM';
    await db.settings.update(1, { viewMode: nextMode });
  };

  const handleDeleteNote = async (id: number) => {
    await db.notes.update(id, { deleted: true, updatedAt: Date.now() });
    await db.tasks.where('sourceNoteId').equals(id).modify({ deleted: true, updatedAt: Date.now() });
    setSelectedNoteId(null);
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

  return (
    <div className="min-h-screen bg-paper flex flex-col text-ink selection:bg-rust selection:text-white">
      {/* Full-screen note: shelf notes (all sizes, focused writer) or any note on mobile */}
      {selectedNoteId && (
        <div className={`fixed inset-0 z-40 bg-paper animate-fade-in ${focusMode ? '' : 'lg:hidden'}`}>
          <NoteDetail
            noteId={selectedNoteId}
            settings={settings}
            onClose={() => setSelectedNoteId(null)}
            onDeleteNote={handleDeleteNote}
            onTalkToEdit={() => openTalkToEdit(selectedNoteId)}
          />
        </div>
      )}

      {/* Main Responsive Canvas */}
      <div className="flex-1 flex flex-col lg:flex-row w-full">
        {/* Left Pane: Stream / Grid River */}
        <div
          className={`flex-1 flex flex-col transition-all ${
            selectedNoteId && !focusMode ? 'lg:w-[45%] lg:border-r lg:border-ink-hairline' : 'w-full'
          }`}
        >
          <Header
            openCount={openCount}
            activeFilter={filter}
            onFilterChange={setFilter}
            viewMode={settings.viewMode}
            onToggleViewMode={handleToggleViewMode}
            onOpenRecall={() => setShowRecall(true)}
            onOpenSettings={() => setShowSettings(true)}
            isConfigured={!!settings.apiKey}
            view={view}
            onToggleShelf={() => setView((v) => (v === 'shelf' ? 'stream' : 'shelf'))}
          />

          <main className="flex-1 overflow-y-auto pb-28">
            {view === 'shelf' ? (
              <ShelfView
                notes={shelfNotes}
                onOpenNote={(id) => setSelectedNoteId(id)}
                onNewShelfNote={handleNewShelfNote}
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
                detailOpen={selectedNoteId !== null && !focusMode}
              />
            )}
          </main>
        </div>

        {/* Right Pane (Desktop 2-pane): a stream note. Shelf notes take the full-screen writer instead. */}
        {selectedNoteId && !focusMode && (
          <div className="hidden lg:flex flex-1 flex-col h-screen sticky top-0 bg-paper">
            <NoteDetail
              noteId={selectedNoteId}
              settings={settings}
              onClose={() => setSelectedNoteId(null)}
              onDeleteNote={handleDeleteNote}
              onTalkToEdit={() => openTalkToEdit(selectedNoteId)}
            />
          </div>
        )}
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

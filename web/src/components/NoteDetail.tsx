import React, { useState, useEffect, useRef } from 'react';
import { useLiveQuery } from 'dexie-react-hooks';
import { TaskEntity, SettingsEntity, db } from '../db/db';
import { ArrowLeft, Trash2, Star, Plus, Mic, Sparkles, Library, ChevronDown, Archive } from 'lucide-react';
import { Markdown } from './Markdown';
import { shapeNote } from '../ai/groq';
import { setShelf } from '../db/actions';
import { scheduleSync } from '../sync/sync';

interface NoteDetailProps {
  noteId: number;
  settings: SettingsEntity;
  onClose: () => void;
  onDeleteNote: (noteId: number) => void;
  onArchiveNote?: (noteId: number) => void;
  onTalkToEdit?: () => void;
}

export const NoteDetail: React.FC<NoteDetailProps> = ({
  noteId,
  settings,
  onClose,
  onDeleteNote,
  onArchiveNote,
  onTalkToEdit,
}) => {
  // Reactive — reflects external changes (voice append/edit) immediately.
  const note = useLiveQuery(() => db.notes.get(noteId), [noteId]);
  const tasks: TaskEntity[] =
    useLiveQuery(() => db.tasks.where('sourceNoteId').equals(noteId).toArray(), [noteId])?.filter(
      (t) => !t.deleted
    ) || [];

  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [newTaskTitle, setNewTaskTitle] = useState('');
  const [editingBody, setEditingBody] = useState(false);
  const [shaping, setShaping] = useState(false);
  const [heardExpanded, setHeardExpanded] = useState(false);
  const saveTimeoutRef = useRef<number | null>(null);
  const titleFocused = useRef(false);
  const bodyFocused = useRef(false);

  // Seed the editor from the note; re-seed when it changes externally (voice edit), but skip a
  // field you're actively typing in so your cursor isn't clobbered.
  useEffect(() => {
    if (!note) return;
    if (!titleFocused.current) setTitle(note.title);
    if (!bodyFocused.current) setBody(note.body);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [noteId, note?.updatedAt]);

  const handleTitleChange = (newTitle: string) => {
    setTitle(newTitle);
    triggerAutoSave(newTitle, body);
  };

  const handleBodyChange = (newBody: string) => {
    setBody(newBody);
    triggerAutoSave(title, newBody);
  };

  const triggerAutoSave = (t: string, b: string) => {
    if (saveTimeoutRef.current) clearTimeout(saveTimeoutRef.current);
    saveTimeoutRef.current = window.setTimeout(async () => {
      await db.notes.update(noteId, {
        // Title is optional — leave it empty (Shape / display fill it in).
        title: t.trim(),
        body: b,
        updatedAt: Date.now(),
      });
      scheduleSync(1000);
    }, 300);
  };

  const handleTogglePin = async () => {
    if (!note) return;
    await db.notes.update(noteId, { pinnedToWidget: !note.pinnedToWidget, updatedAt: Date.now() });
    scheduleSync(500);
  };

  // Reformat the raw prose into structured Markdown, on demand.
  const handleShape = async () => {
    if (shaping) return;
    // Rebuild from the raw transcript when we have one — recovers detail an earlier tidy dropped.
    const source = note?.heardAs?.trim() ? note.heardAs : body;
    if (!source.trim()) return;
    setShaping(true);
    const r = await shapeNote({
      title,
      body: source,
      apiKey: settings.apiKey,
      baseUrl: settings.baseUrl,
      model: settings.model,
      extractTasks: false,
    });
    await db.notes.update(noteId, { title: r.title, body: r.body, updatedAt: Date.now() });
    setShaping(false);
    scheduleSync(0);
  };

  const handleMoveShelf = async () => {
    if (!note) return;
    await setShelf(noteId, !note.shelf);
    scheduleSync(500);
  };

  // Backing out of a still-blank note discards it (e.g. a shelf note you opened but never wrote in).
  const closeNote = async () => {
    if (!title.trim() && !body.trim() && tasks.length === 0) {
      // ponytail: soft-delete tombstone prevents Drive resurrecting discarded blank notes
      await db.notes.update(noteId, { deleted: true, updatedAt: Date.now() });
      scheduleSync(0);
    }
    onClose();
  };

  const handleToggleTask = async (taskId: number, currentDone: boolean) => {
    await db.tasks.update(taskId, {
      done: !currentDone,
      doneAt: !currentDone ? Date.now() : null,
      updatedAt: Date.now(),
    });
    scheduleSync(500);
  };

  const handleAddTask = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTaskTitle.trim()) return;
    const now = Date.now();
    await db.tasks.add({
      title: newTaskTitle.trim(),
      done: false,
      sourceNoteId: noteId,
      createdAt: now,
      updatedAt: now,
      deleted: false,
    });
    setNewTaskTitle('');
    scheduleSync(500);
  };

  if (!note || note.deleted) return null;

  const dateStr = new Date(note.createdAt).toLocaleDateString('en-US', {
    weekday: 'long',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });

  // Drop a leading "# Title" heading from the body when it just repeats the note title.
  const displayBody = (() => {
    const m = body.match(/^\s*#\s+(.+?)\s*\n+/);
    if (m && m[1].trim().toLowerCase() === (title || '').trim().toLowerCase()) {
      return body.slice(m[0].length);
    }
    return body;
  })();

  const handleDelete = () => {
    if (saveTimeoutRef.current) {
      clearTimeout(saveTimeoutRef.current);
      saveTimeoutRef.current = null;
    }
    onClose();
    onDeleteNote(noteId);
  };

  const handleArchive = () => {
    if (saveTimeoutRef.current) {
      clearTimeout(saveTimeoutRef.current);
      saveTimeoutRef.current = null;
    }
    onClose();
    onArchiveNote?.(noteId);
  };

  return (
    <div className="h-full flex flex-col bg-paper">
      {/* Top Bar with Clean Icon-Only Action Buttons */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-ink-hairline bg-paper">
        <button
          onClick={closeNote}
          className="p-2 rounded-full text-ink-muted hover:text-ink hover:bg-ink/[0.05] transition-colors"
          title="Back (Esc)"
        >
          <ArrowLeft className="w-4 h-4" />
        </button>

        <div className="flex items-center gap-1 text-ink-muted">
          {onTalkToEdit && (
            <button
              onClick={onTalkToEdit}
              className="p-2 rounded-full hover:text-ink hover:bg-ink/[0.05] transition-colors"
              title="Talk to edit note"
            >
              <Mic className="w-4 h-4" />
            </button>
          )}

          {note.shelf && (
            <button
              onClick={handleShape}
              disabled={shaping || !body.trim()}
              className="p-2 rounded-full hover:text-rust hover:bg-rust-muted/50 transition-colors disabled:opacity-40"
              title="Shape Markdown"
            >
              <Sparkles className="w-4 h-4 text-rust" />
            </button>
          )}

          <button
            onClick={handleMoveShelf}
            className="p-2 rounded-full hover:text-ink hover:bg-ink/[0.05] transition-colors"
            title={note.shelf ? 'Move to Stream' : 'Move to Shelf'}
          >
            <Library className="w-4 h-4" />
          </button>

          {!note.shelf && (
            <button
              onClick={handleTogglePin}
              className={`p-2 rounded-full hover:bg-ink/[0.05] transition-colors ${
                note.pinnedToWidget ? 'text-rust' : 'hover:text-ink'
              }`}
              title={note.pinnedToWidget ? 'Unpin from Widget' : 'Pin to Widget'}
            >
              <Star className={`w-4 h-4 ${note.pinnedToWidget ? 'fill-rust' : ''}`} />
            </button>
          )}

          {onArchiveNote && (
            <button
              onClick={handleArchive}
              className="p-2 rounded-full hover:text-ink hover:bg-ink/[0.05] transition-colors"
              title="Archive note"
            >
              <Archive className="w-4 h-4" />
            </button>
          )}

          <button
            onClick={handleDelete}
            className="p-2 rounded-full text-rust/80 hover:text-rust hover:bg-rust-muted transition-colors"
            title="Delete note"
          >
            <Trash2 className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Editor Content */}
      <div className="flex-1 overflow-y-auto px-6 py-8 max-w-3xl mx-auto w-full space-y-6">
        {/* Title */}
        <input
          type="text"
          value={title}
          onChange={(e) => handleTitleChange(e.target.value)}
          onFocus={() => (titleFocused.current = true)}
          onBlur={() => (titleFocused.current = false)}
          placeholder="Untitled note"
          className="w-full bg-transparent font-serif text-note-title text-ink placeholder:text-ink-faint focus:outline-none"
        />

        <div className="font-mono text-meta text-ink-faint">
          {dateStr}
        </div>

        <hr className="border-ink-hairline" />

        {/* Body — rendered markdown by default, click to edit */}
        {editingBody ? (
          <textarea
            value={body}
            onChange={(e) => handleBodyChange(e.target.value)}
            onFocus={() => (bodyFocused.current = true)}
            onBlur={() => {
              bodyFocused.current = false;
              setEditingBody(false);
            }}
            autoFocus
            placeholder="Write your thoughts here… (markdown supported)"
            rows={8}
            className="w-full bg-transparent font-serif text-body text-ink placeholder:text-ink-faint focus:outline-none resize-none"
          />
        ) : body.trim() ? (
          <div onClick={() => setEditingBody(true)} className="cursor-text">
            <Markdown>{displayBody}</Markdown>
          </div>
        ) : (
          <button
            onClick={() => setEditingBody(true)}
            className="font-serif text-body text-ink-faint text-left w-full"
          >
            Add a note…
          </button>
        )}

        {/* Tasks in this note */}
        <div className="pt-4 space-y-3">
          <div className="font-mono text-label text-ink-faint">
            Tasks in this note ({tasks.length})
          </div>

          <div className="space-y-2">
            {tasks.map((task) => (
              <div
                key={task.id}
                className="flex items-center gap-3 p-2 rounded-lg hover:bg-paper-card group"
              >
                <input
                  type="checkbox"
                  checked={task.done}
                  onChange={() => handleToggleTask(task.id!, task.done)}
                  className="hark-check"
                />
                <span
                  className={`flex-1 font-serif text-item text-ink ${
                    task.done ? 'line-through text-ink-faint' : ''
                  }`}
                >
                  {task.title}
                </span>
                {task.dueHint && (
                  <span className="font-mono text-meta text-rust font-medium">
                    {task.dueHint}
                  </span>
                )}
              </div>
            ))}
          </div>

          {/* Quick add task input */}
          <form onSubmit={handleAddTask} className="flex gap-2 pt-2">
            <input
              type="text"
              value={newTaskTitle}
              onChange={(e) => setNewTaskTitle(e.target.value)}
              placeholder="Add task..."
              className="flex-1 px-3.5 py-2 rounded-xl border border-ink-hairline bg-paper-card text-secondary font-serif text-ink placeholder:text-ink-faint focus:outline-none focus:border-ink"
            />
            <button
              type="submit"
              disabled={!newTaskTitle.trim()}
              className="px-4 py-2 rounded-xl bg-ink text-paper font-mono text-label font-medium disabled:opacity-40 hover:opacity-90 flex items-center gap-1"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Add</span>
            </button>
          </form>
        </div>

        {/* Heard As transcript — collapsed by default */}
        {note.heardAs && (
          <div className="pt-6 border-t border-ink-hairline space-y-2">
            <button
              onClick={() => setHeardExpanded((v) => !v)}
              className="flex items-center gap-1.5 font-mono text-label text-ink-faint hover:text-ink-muted"
            >
              <span>Heard as</span>
              <ChevronDown className={`w-3.5 h-3.5 transition-transform ${heardExpanded ? 'rotate-180' : ''}`} />
            </button>
            {heardExpanded && (
              <p className="font-serif text-secondary text-ink-muted italic">"{note.heardAs}"</p>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

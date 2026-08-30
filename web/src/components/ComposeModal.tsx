import React, { useState } from 'react';
import { SettingsEntity, db } from '../db/db';
import { shapeNote } from '../ai/groq';
import { scheduleSync } from '../sync/sync';
import { X, Sparkles } from 'lucide-react';

interface ComposeModalProps {
  settings: SettingsEntity;
  onClose: () => void;
  onSaved: (noteId?: number) => void;
}

export const ComposeModal: React.FC<ComposeModalProps> = ({
  settings,
  onClose,
  onSaved,
}) => {
  const [mode, setMode] = useState<'NOTE' | 'TASK'>('NOTE');
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [dueHint, setDueHint] = useState('');
  const [extractTasks, setExtractTasks] = useState(true);
  const [isTidying, setIsTidying] = useState(false);

  const handleSavePlainNote = async () => {
    if (!title.trim() && !body.trim()) return;
    const now = Date.now();
    const noteId = await db.notes.add({
      title: title.trim() || 'Untitled note',
      body: body.trim(),
      source: 'TYPED',
      pinnedToWidget: false,
      shelf: false,
      createdAt: now,
      updatedAt: now,
      deleted: false,
    });
    scheduleSync(500);
    onSaved(noteId);
  };

  const handleTidyAndSave = async () => {
    if (!title.trim() && !body.trim()) return;
    setIsTidying(true);

    try {
      const result = await shapeNote({
        title,
        body,
        apiKey: settings.apiKey,
        baseUrl: settings.baseUrl,
        model: settings.model,
        extractTasks,
      });

      const now = Date.now();
      const noteId = await db.notes.add({
        title: result.title || 'Untitled note',
        body: result.body,
        source: 'TYPED',
        pinnedToWidget: false,
        shelf: false,
        createdAt: now,
        updatedAt: now,
        deleted: false,
      });

      if (result.tasks.length > 0) {
        await db.tasks.bulkAdd(
          result.tasks.map((t: { title: string; dueHint?: string | null }) => ({
            title: t.title,
            done: false,
            dueHint: t.dueHint ?? null,
            sourceNoteId: noteId,
            createdAt: now,
            updatedAt: now,
            deleted: false,
          }))
        );
      }

      scheduleSync(0);
      onSaved(noteId);
    } catch {
      await handleSavePlainNote();
    } finally {
      setIsTidying(false);
    }
  };

  const handleSaveTask = async () => {
    if (!title.trim()) return;
    const now = Date.now();
    const taskId = await db.tasks.add({
      title: title.trim(),
      done: false,
      dueHint: dueHint.trim() || null,
      createdAt: now,
      updatedAt: now,
      deleted: false,
    });
    scheduleSync(500);
    onSaved(taskId);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-ink/60 backdrop-blur-md animate-fade-in">
      <div className="bg-paper border border-ink-hairline rounded-3xl p-7 max-w-lg w-full shadow-2xl space-y-6">
        {/* Top bar & Mode toggle */}
        <div className="flex items-center justify-between">
          <div className="flex gap-4 font-mono text-label">
            <button
              onClick={() => setMode('NOTE')}
              className={`pb-1 border-b transition-colors font-medium ${
                mode === 'NOTE' ? 'border-ink text-ink font-semibold' : 'border-transparent text-ink-faint'
              }`}
            >
              Note
            </button>
            <button
              onClick={() => setMode('TASK')}
              className={`pb-1 border-b transition-colors font-medium ${
                mode === 'TASK' ? 'border-ink text-ink font-semibold' : 'border-transparent text-ink-faint'
              }`}
            >
              Checklist item
            </button>
          </div>

          <button
            onClick={onClose}
            className="p-1 rounded-full text-ink-faint hover:text-ink transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {mode === 'NOTE' ? (
          <div className="space-y-4">
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Title..."
              className="w-full bg-transparent font-serif text-note-title text-ink placeholder:text-ink-faint focus:outline-none"
            />
            <textarea
              value={body}
              onChange={(e) => setBody(e.target.value)}
              placeholder="Write your note or brain dump..."
              rows={6}
              className="w-full bg-transparent font-serif text-body text-ink placeholder:text-ink-faint focus:outline-none resize-none"
            />

            <button
              type="button"
              onClick={() => setExtractTasks((v) => !v)}
              className="font-mono text-meta text-ink-muted self-start font-medium"
            >
              Extract tasks: <span className={extractTasks ? 'text-rust font-semibold' : 'text-ink-faint'}>{extractTasks ? 'On' : 'Off'}</span>
            </button>

            <div className="pt-1 flex items-center gap-3">
              <button
                onClick={handleSavePlainNote}
                disabled={!title.trim() && !body.trim()}
                className="flex-1 py-3 rounded-xl border border-ink-hairline bg-paper-card text-ink font-mono text-label font-medium hover:opacity-90 disabled:opacity-40"
              >
                Save
              </button>

              <button
                onClick={handleTidyAndSave}
                disabled={(!title.trim() && !body.trim()) || isTidying}
                className="flex-1 py-3 rounded-xl bg-ink text-paper font-mono text-label font-medium hover:opacity-90 disabled:opacity-40 flex items-center justify-center gap-1.5"
              >
                <Sparkles className="w-3.5 h-3.5 text-rust" />
                <span>{isTidying ? 'Tidying…' : 'Tidy & save'}</span>
              </button>
            </div>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="space-y-1.5">
              <label className="font-mono text-label text-ink-faint">
                Task description
              </label>
              <input
                type="text"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="e.g. buy oat milk and eggs"
                className="w-full px-3.5 py-2.5 rounded-xl border border-ink-hairline bg-paper-card text-secondary font-serif text-ink placeholder:text-ink-faint focus:outline-none focus:border-ink"
              />
            </div>

            <div className="space-y-1.5">
              <label className="font-mono text-label text-ink-faint">
                Due / reminder hint (optional)
              </label>
              <input
                type="text"
                value={dueHint}
                onChange={(e) => setDueHint(e.target.value)}
                placeholder="e.g. today 5pm, tomorrow, friday"
                className="w-full px-3.5 py-2.5 rounded-xl border border-ink-hairline bg-paper-card text-secondary font-serif text-ink placeholder:text-ink-faint focus:outline-none focus:border-ink"
              />
            </div>

            <div className="pt-3">
              <button
                onClick={handleSaveTask}
                disabled={!title.trim()}
                className="w-full py-3 rounded-xl bg-ink text-paper font-mono text-label font-medium hover:opacity-90 disabled:opacity-40"
              >
                Create checklist item
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

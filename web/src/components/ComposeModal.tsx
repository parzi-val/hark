import React, { useState } from 'react';
import { db, SettingsEntity } from '../db/db';
import { processCapture } from '../ai/groq';
import { recentNoteRefs, applyAction } from '../db/actions';
import { X, Sparkles } from 'lucide-react';

interface ComposeModalProps {
  settings: SettingsEntity;
  onClose: () => void;
  onSaved: (id: number) => void;
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
  const [isTidying, setIsTidying] = useState(false);
  const [extractTasks, setExtractTasks] = useState(true);

  const handleSavePlainNote = async () => {
    if (!title.trim() && !body.trim()) return;
    const now = Date.now();
    const noteId = await db.notes.add({
      title: title.trim() || 'Untitled note',
      body: body.trim(),
      source: 'TYPED',
      pinnedToWidget: false,
      createdAt: now,
      updatedAt: now,
      deleted: false,
    });
    onSaved(noteId);
  };

  const handleTidyAndSave = async () => {
    const rawText = `${title}\n\n${body}`.trim();
    if (!rawText) return;

    if (!settings.apiKey) {
      await handleSavePlainNote();
      return;
    }

    setIsTidying(true);
    try {
      const notes = await recentNoteRefs();
      const action = await processCapture({
        transcript: rawText,
        apiKey: settings.apiKey,
        baseUrl: settings.baseUrl,
        model: settings.model,
        extractTasks,
        notes,
      });
      const noteId = await applyAction(action, rawText, 'TYPED');
      onSaved(noteId);
    } catch (err) {
      console.error('Tidy failed, saving raw note:', err);
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
      sourceNoteId: null,
      createdAt: now,
      updatedAt: now,
      deleted: false,
    });
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
              className={`pb-1 border-b transition-colors ${
                mode === 'NOTE' ? 'border-ink text-ink' : 'border-transparent text-ink-faint'
              }`}
            >
              NOTE
            </button>
            <button
              onClick={() => setMode('TASK')}
              className={`pb-1 border-b transition-colors ${
                mode === 'TASK' ? 'border-ink text-ink' : 'border-transparent text-ink-faint'
              }`}
            >
              CHECKLIST ITEM
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
              className="font-mono text-meta uppercase text-ink-muted self-start"
            >
              Extract tasks: <span className={extractTasks ? 'text-rust' : 'text-ink-faint'}>{extractTasks ? 'ON' : 'OFF'}</span>
            </button>

            <div className="pt-1 flex items-center gap-3">
              <button
                onClick={handleSavePlainNote}
                disabled={!title.trim() && !body.trim()}
                className="flex-1 py-3 rounded-xl border border-ink-hairline bg-paper-card text-ink font-mono text-label hover:opacity-90 disabled:opacity-40"
              >
                SAVE
              </button>

              <button
                onClick={handleTidyAndSave}
                disabled={(!title.trim() && !body.trim()) || isTidying}
                className="flex-1 py-3 rounded-xl bg-ink text-paper font-mono text-label hover:opacity-90 disabled:opacity-40 flex items-center justify-center gap-1.5"
              >
                <Sparkles className="w-3.5 h-3.5 text-rust" />
                <span>{isTidying ? 'TIDYING…' : 'TIDY & SAVE'}</span>
              </button>
            </div>
          </div>
        ) : (
          <div className="space-y-4">
            <div className="space-y-1.5">
              <label className="font-mono text-label text-ink-faint uppercase">
                TASK DESCRIPTION
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
              <label className="font-mono text-label text-ink-faint uppercase">
                DUE / REMINDER HINT (OPTIONAL)
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
                className="w-full py-3 rounded-xl bg-ink text-paper font-mono text-label hover:opacity-90 disabled:opacity-40"
              >
                CREATE CHECKLIST ITEM
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

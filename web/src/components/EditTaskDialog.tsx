import React, { useState } from 'react';
import { TaskEntity, db } from '../db/db';
import { taskStatus, setTaskStatus, TaskStatus } from '../db/actions';
import { scheduleSync } from '../sync/sync';
import { X, Trash2 } from 'lucide-react';

interface EditTaskDialogProps {
  task: TaskEntity;
  onClose: () => void;
  onUpdated: () => void;
}

export const EditTaskDialog: React.FC<EditTaskDialogProps> = ({
  task,
  onClose,
  onUpdated,
}) => {
  const [title, setTitle] = useState(task.title);
  const [dueHint, setDueHint] = useState(task.dueHint || '');
  const [status, setStatus] = useState<TaskStatus>(taskStatus(task));

  const handleSave = async () => {
    if (!title.trim() || task.id == null) return;
    const now = Date.now();
    await db.tasks.update(task.id, {
      title: title.trim(),
      dueHint: dueHint.trim() || null,
      updatedAt: now,
    });
    await setTaskStatus(task.id, status);
    scheduleSync(500);
    onUpdated();
    onClose();
  };

  const handleDelete = async () => {
    if (task.id == null) return;
    await db.tasks.update(task.id, {
      deleted: true,
      updatedAt: Date.now(),
    });
    scheduleSync(0);
    onUpdated();
    onClose();
  };

  const statusOptions: { value: TaskStatus; label: string }[] = [
    { value: 'OPEN', label: 'Open' },
    { value: 'DEFERRED', label: 'Deferred' },
    { value: 'COMPLETED', label: 'Completed' },
  ];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-ink/60 backdrop-blur-md animate-fade-in">
      <div className="bg-paper border border-ink-hairline rounded-3xl p-7 max-w-md w-full shadow-2xl space-y-5">
        {/* Top bar */}
        <div className="flex items-center justify-between">
          <span className="font-mono text-label text-ink font-semibold">
            Edit task
          </span>
          <button
            onClick={onClose}
            className="p-1 rounded-full text-ink-faint hover:text-ink transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="space-y-4">
          {/* Status Segmented Control (Mobile & Desktop State Change) */}
          <div className="space-y-1.5">
            <label className="font-mono text-label text-ink-faint">
              Status
            </label>
            <div className="grid grid-cols-3 gap-1.5 p-1 rounded-xl border border-ink-hairline bg-paper-card">
              {statusOptions.map((opt) => (
                <button
                  key={opt.value}
                  type="button"
                  onClick={() => setStatus(opt.value)}
                  className={`py-1.5 rounded-lg font-mono text-micro transition-all ${
                    status === opt.value
                      ? 'bg-ink text-paper font-semibold shadow-xs'
                      : 'text-ink-muted hover:text-ink'
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          <div className="space-y-1.5">
            <label className="font-mono text-label text-ink-faint">
              Description
            </label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="w-full px-3.5 py-2.5 rounded-xl border border-ink-hairline bg-paper-card text-secondary font-serif text-ink focus:outline-none focus:border-ink"
            />
          </div>

          <div className="space-y-1.5">
            <label className="font-mono text-label text-ink-faint">
              Due / reminder hint
            </label>
            <input
              type="text"
              value={dueHint}
              onChange={(e) => setDueHint(e.target.value)}
              placeholder="e.g. today 5pm, tomorrow, friday"
              className="w-full px-3.5 py-2.5 rounded-xl border border-ink-hairline bg-paper-card text-secondary font-serif text-ink placeholder:text-ink-faint focus:outline-none focus:border-ink"
            />
          </div>
        </div>

        {/* Actions */}
        <div className="pt-2 flex items-center justify-between gap-3">
          <button
            onClick={handleDelete}
            className="flex items-center gap-1 text-rust font-mono text-label font-medium hover:opacity-80 px-2 py-2"
          >
            <Trash2 className="w-3.5 h-3.5" />
            <span>Delete</span>
          </button>

          <div className="flex items-center gap-2">
            <button
              onClick={onClose}
              className="px-4 py-2.5 rounded-xl border border-ink-hairline text-ink-muted hover:text-ink font-mono text-label font-medium"
            >
              Cancel
            </button>
            <button
              onClick={handleSave}
              disabled={!title.trim()}
              className="px-5 py-2.5 rounded-xl bg-ink text-paper font-mono text-label font-medium hover:opacity-90 disabled:opacity-40"
            >
              Save
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

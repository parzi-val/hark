import React from 'react';
import { NoteEntity, TaskEntity } from '../db/db';
import { stripMarkdown } from '../lib/md';

interface GridViewProps {
  notes: NoteEntity[];
  tasks: TaskEntity[];
  filter: 'ALL' | 'OPEN' | 'ARCHIVE';
  onToggleTask: (taskId: number, currentDone: boolean) => void;
  onOpenNote: (noteId: number) => void;
  onEditTask: (task: TaskEntity) => void;
}

export type GridItem =
  | { type: 'NOTE'; note: NoteEntity; tasks: TaskEntity[] }
  | { type: 'TASK'; task: TaskEntity };

export const GridView: React.FC<GridViewProps> = ({
  notes,
  tasks,
  filter,
  onToggleTask,
  onOpenNote,
  onEditTask,
}) => {
  const tasksByNote = tasks.reduce<Record<number, TaskEntity[]>>((acc, t) => {
    if (t.sourceNoteId && !t.deleted) {
      if (!acc[t.sourceNoteId]) acc[t.sourceNoteId] = [];
      acc[t.sourceNoteId].push(t);
    }
    return acc;
  }, {});

  const standaloneTasks = tasks.filter((t) => !t.sourceNoteId && !t.deleted);

  let items: GridItem[] = [];

  if (filter === 'OPEN') {
    items = tasks.filter((t) => !t.done && !t.deleted).map((t) => ({ type: 'TASK', task: t }));
  } else {
    const noteItems: GridItem[] = notes.map((n) => ({
      type: 'NOTE',
      note: n,
      tasks: tasksByNote[n.id!] || [],
    }));
    const taskItems: GridItem[] = standaloneTasks.map((t) => ({
      type: 'TASK',
      task: t,
    }));
    items = [...noteItems, ...taskItems].sort((a, b) => {
      const aTime = a.type === 'NOTE' ? a.note.createdAt : a.task.createdAt;
      const bTime = b.type === 'NOTE' ? b.note.createdAt : b.task.createdAt;
      const aPinned = a.type === 'NOTE' && a.note.pinnedToWidget;
      const bPinned = b.type === 'NOTE' && b.note.pinnedToWidget;
      if (aPinned && !bPinned) return -1;
      if (!aPinned && bPinned) return 1;
      return bTime - aTime;
    });
  }

  if (items.length === 0) {
    return (
      <div className="py-24 text-center">
        <p className="text-ink-faint font-serif text-secondary">
          No thoughts in this view.
        </p>
      </div>
    );
  }

  const renderCard = (item: GridItem) => {
    if (item.type === 'NOTE') {
      const { note, tasks } = item;
      const timeStr = new Date(note.createdAt).toLocaleDateString([], {
        month: 'short',
        day: 'numeric',
      });

      return (
        <div
          className={`flex flex-col justify-between p-5 rounded-2xl border transition-all hover:shadow-md ${
            note.pinnedToWidget
              ? 'border-rust bg-paper-card shadow-xs'
              : 'border-ink-hairline bg-paper-card'
          }`}
        >
          <div
            className="cursor-pointer"
            onClick={() => note.id != null && onOpenNote(note.id)}
          >
            <div className="flex items-baseline justify-between gap-2">
              <h3 className="font-serif text-item font-medium text-ink">
                {note.pinnedToWidget && (
                  <span className="text-rust mr-1.5 font-normal">★</span>
                )}
                {note.title || 'Untitled note'}
              </h3>
            </div>

            {note.body && (
              <p className="mt-2 text-secondary font-serif text-ink-muted line-clamp-6 leading-relaxed">
                {stripMarkdown(note.body)}
              </p>
            )}
          </div>

          {/* Nested tasks */}
          {tasks.length > 0 && (
            <div className="mt-4 pt-3 border-t border-ink-hairline/60 space-y-1.5">
              {tasks.slice(0, 4).map((t) => (
                <div
                  key={`gt-${t.id ?? t.uid}`}
                  className="flex items-center gap-2.5 font-serif"
                >
                  <input
                    type="checkbox"
                    checked={t.done}
                    onChange={() => t.id != null && onToggleTask(t.id, t.done)}
                    className="hark-check"
                  />
                  <span
                    onClick={() => onEditTask(t)}
                    className={`cursor-pointer flex-1 truncate text-secondary ${
                      t.done ? 'line-through text-ink-faint' : 'text-ink'
                    }`}
                  >
                    {t.title}
                  </span>
                  {t.dueHint && (
                    <span className="font-mono text-meta text-rust font-medium">
                      {t.dueHint}
                    </span>
                  )}
                </div>
              ))}
              {tasks.length > 4 && (
                <span className="font-mono text-meta text-ink-faint block pt-1">
                  + {tasks.length - 4} more tasks
                </span>
              )}
            </div>
          )}

          <div className="mt-4 pt-2 flex items-center justify-between font-mono text-meta text-ink-faint border-t border-ink-hairline">
            <span>{timeStr}</span>
            {note.source === 'VOICE' && <span>🎙 Voice</span>}
          </div>
        </div>
      );
    }

    // Standalone Task Card
    const { task } = item;
    return (
      <div className="p-4 rounded-2xl border border-ink-hairline bg-paper-card hover:shadow-md transition-all flex items-center gap-3">
        <input
          type="checkbox"
          checked={task.done}
          onChange={() => task.id != null && onToggleTask(task.id, task.done)}
          className="hark-check"
        />
        <span
          onClick={() => onEditTask(task)}
          className={`cursor-pointer flex-1 font-serif text-item text-ink ${
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
    );
  };

  // ponytail: robust CSS multi-column masonry — zero JS absolute positioning, zero overlapping, zero crash
  return (
    <div className="w-full max-w-[1600px] mx-auto px-6 lg:px-10 py-6 animate-fade-in columns-1 sm:columns-2 lg:columns-3 xl:columns-4 gap-4">
      {items.map((item) => (
        <div
          key={item.type === 'NOTE' ? `n-${item.note.id ?? item.note.uid}` : `t-${item.task.id ?? item.task.uid}`}
          className="break-inside-avoid-column mb-4"
        >
          {renderCard(item)}
        </div>
      ))}
    </div>
  );
};

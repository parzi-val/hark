import React from 'react';
import { TaskEntity, NoteEntity } from '../db/db';
import { setTaskStatus, taskStatus } from '../db/actions';
import { Check } from 'lucide-react';

interface NoteTaskGroupProps {
  note?: NoteEntity | null;
  tasks: TaskEntity[];
  onOpenNote?: (noteId: number) => void;
  onEditTask?: (task: TaskEntity) => void;
  draggable?: boolean;
}

export const NoteTaskGroup: React.FC<NoteTaskGroupProps> = ({
  note,
  tasks,
  onOpenNote,
  onEditTask,
  draggable = false,
}) => {
  if (tasks.length === 0) return null;

  const handleToggle = (e: React.MouseEvent, t: TaskEntity) => {
    e.stopPropagation();
    if (t.id == null) return;
    const current = taskStatus(t);
    setTaskStatus(t.id, current === 'COMPLETED' ? 'OPEN' : 'COMPLETED');
  };

  const renderTaskRow = (t: TaskEntity) => {
    const status = taskStatus(t);
    const isCompleted = status === 'COMPLETED';
    const isDeferred = status === 'DEFERRED';

    return (
      <div
        key={t.id ?? t.uid}
        draggable={draggable}
        onDragStart={(e) => {
          if (t.id != null) {
            e.dataTransfer.setData('text/plain', String(t.id));
            e.dataTransfer.effectAllowed = 'move';
          }
        }}
        onClick={() => onEditTask?.(t)}
        className={`group flex items-start gap-2.5 p-2 rounded-lg hover:bg-ink/[0.03] transition-colors cursor-pointer select-none ${
          isDeferred ? 'text-ink-muted' : isCompleted ? 'text-ink-faint' : 'text-ink'
        }`}
      >
        {/* Checkbox button */}
        <button
          type="button"
          onClick={(e) => handleToggle(e, t)}
          className={`mt-0.5 w-4 h-4 rounded border flex items-center justify-center transition-colors shrink-0 ${
            isCompleted
              ? 'bg-ink border-ink text-paper'
              : isDeferred
              ? 'border-ink-muted/70 hover:border-ink'
              : 'border-checkbox-border hover:border-ink'
          }`}
        >
          {isCompleted && <Check className="w-3 h-3 stroke-[3]" />}
        </button>

        {/* Task Title + Due Hint */}
        <div className="flex-1 min-w-0 flex items-baseline justify-between gap-2">
          <span
            className={`font-serif text-item leading-snug break-words ${
              isCompleted ? 'line-through text-ink-faint' : ''
            }`}
          >
            {t.title}
          </span>
          {t.dueHint && (
            <span className="font-mono text-micro text-rust font-medium shrink-0">
              {t.dueHint}
            </span>
          )}
        </div>
      </div>
    );
  };

  // Loose task (no parent note) — render as bare rows without card border (per spec §6a)
  if (!note || note.id == null) {
    return <div className="space-y-1">{tasks.map(renderTaskRow)}</div>;
  }

  // Grouped task under a parent note — render inside bordered card container
  return (
    <div className="rounded-xl border border-ink-hairline bg-paper-card p-3 space-y-2 shadow-xs transition-shadow hover:border-ink-muted/40">
      {/* Note Header */}
      <div
        onClick={() => note.id != null && onOpenNote?.(note.id)}
        className="flex items-center justify-between gap-2 cursor-pointer group/hdr pb-1 border-b border-ink-hairline"
        title="Open parent note"
      >
        <span className="font-serif text-xs font-medium text-ink group-hover/hdr:text-rust transition-colors truncate">
          {note.title || 'Untitled note'}
        </span>
        <span className="font-mono text-micro text-ink-faint shrink-0">
          {tasks.length} {tasks.length === 1 ? 'task' : 'tasks'}
        </span>
      </div>

      {/* Task Rows */}
      <div className="space-y-0.5">{tasks.map(renderTaskRow)}</div>
    </div>
  );
};

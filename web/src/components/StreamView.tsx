import React from 'react';
import { NoteEntity, TaskEntity } from '../db/db';
import { stripMarkdown } from '../lib/md';

interface StreamViewProps {
  notes: NoteEntity[];
  tasks: TaskEntity[];
  filter: 'ALL' | 'OPEN' | 'ARCHIVE';
  onToggleTask: (taskId: number, currentDone: boolean) => void;
  onOpenNote: (noteId: number) => void;
  onEditTask: (task: TaskEntity) => void;
}

export const StreamView: React.FC<StreamViewProps> = ({
  notes,
  tasks,
  filter,
  onToggleTask,
  onOpenNote,
  onEditTask,
}) => {
  // Map tasks to parent notes
  const tasksByNote = tasks.reduce<Record<number, TaskEntity[]>>((acc, t) => {
    if (t.sourceNoteId) {
      if (!acc[t.sourceNoteId]) acc[t.sourceNoteId] = [];
      acc[t.sourceNoteId].push(t);
    }
    return acc;
  }, {});

  const standaloneTasks = tasks.filter((t) => !t.sourceNoteId);

  // Filter items
  type StreamItem =
    | { type: 'NOTE'; note: NoteEntity; tasks: TaskEntity[] }
    | { type: 'TASK'; task: TaskEntity };

  let items: StreamItem[] = [];

  if (filter === 'OPEN') {
    // Flatten all open tasks
    const openTasks = tasks.filter((t) => !t.done);
    items = openTasks.map((t) => ({ type: 'TASK', task: t }));
  } else {
    // ALL
    const noteItems: StreamItem[] = notes.map((n) => ({
      type: 'NOTE',
      note: n,
      tasks: tasksByNote[n.id!] || [],
    }));
    const taskItems: StreamItem[] = standaloneTasks.map((t) => ({
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

  // Partition into PINNED, TODAY, EARLIER
  const todayStart = new Date();
  todayStart.setHours(0, 0, 0, 0);

  const pinned = items.filter(
    (i) => i.type === 'NOTE' && i.note.pinnedToWidget
  );
  const unpinned = items.filter(
    (i) => !(i.type === 'NOTE' && i.note.pinnedToWidget)
  );

  const todayItems = unpinned.filter((i) => {
    const time = i.type === 'NOTE' ? i.note.createdAt : i.task.createdAt;
    return time >= todayStart.getTime();
  });

  const earlierItems = unpinned.filter((i) => {
    const time = i.type === 'NOTE' ? i.note.createdAt : i.task.createdAt;
    return time < todayStart.getTime();
  });

  const groups = [
    { label: 'Pinned', list: pinned },
    { label: 'Today', list: todayItems },
    { label: 'Earlier', list: earlierItems },
  ].filter((g) => g.list.length > 0);

  if (groups.length === 0) {
    return (
      <div className="py-24 text-center">
        <p className="text-ink-faint font-serif text-secondary">
          Nothing here yet. Tap Talk to capture your mind.
        </p>
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto px-6 py-6 divide-y divide-ink-hairline">
      {groups.map((group) => (
        <div key={group.label} className="pt-6 pb-2">
          <div className="px-6 mb-3 font-mono text-label text-ink-faint font-medium">
            {group.label}
          </div>

          <div className="divide-y divide-ink-hairline">
            {group.list.map((item) => {
              if (item.type === 'NOTE') {
                const { note, tasks } = item;
                const timeStr = new Date(note.createdAt).toLocaleTimeString([], {
                  hour: '2-digit',
                  minute: '2-digit',
                });

                return (
                  <div key={`n-${note.id}`} className="py-4 px-6 hover:bg-paper-card transition-colors rounded-xl">
                    <div
                      className="cursor-pointer flex items-start gap-3.5"
                      onClick={() => onOpenNote(note.id!)}
                    >
                      {/* Dash prefix */}
                      <span
                        className={`inline-block w-4 h-px mt-2.5 flex-shrink-0 ${
                          note.pinnedToWidget ? 'bg-rust' : 'bg-ink-faint'
                        }`}
                      />

                      <div className="flex-1 min-w-0">
                        <div className="text-item font-serif text-ink">
                          {note.pinnedToWidget && (
                            <span className="text-rust mr-1.5">★</span>
                          )}
                          {note.title}
                        </div>

                        {note.body && (
                          <p className="text-secondary font-serif text-ink-muted line-clamp-2 mt-1">
                            {stripMarkdown(note.body)}
                          </p>
                        )}

                        <div className="font-mono text-meta text-ink-faint mt-1.5">
                          {timeStr}
                        </div>
                      </div>
                    </div>

                    {/* Nested tasks */}
                    {tasks.length > 0 && (
                      <div className="mt-2 pl-7 space-y-2">
                        {tasks.map((t) => (
                          <div
                            key={`t-${t.id}`}
                            className="flex items-center gap-3 group"
                          >
                            <input
                              type="checkbox"
                              checked={t.done}
                              onChange={() => onToggleTask(t.id!, t.done)}
                              className="hark-check"
                            />
                            <span
                              onClick={() => onEditTask(t)}
                              className={`cursor-pointer flex-1 font-serif text-item text-ink ${
                                t.done ? 'line-through text-ink-faint' : ''
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
                      </div>
                    )}
                  </div>
                );
              } else {
                // Standalone Task
                const { task } = item;
                return (
                  <div
                    key={`st-${task.id}`}
                    className="py-4 px-6 flex items-center gap-3.5 hover:bg-paper-card transition-colors rounded-xl"
                  >
                    <input
                      type="checkbox"
                      checked={task.done}
                      onChange={() => onToggleTask(task.id!, task.done)}
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
              }
            })}
          </div>
        </div>
      ))}
    </div>
  );
};

import React, { useState } from 'react';
import { TaskEntity, NoteEntity } from '../db/db';
import { taskStatus, setTaskStatus, TaskStatus } from '../db/actions';
import { NoteTaskGroup } from './NoteTaskGroup';

interface KanbanBoardProps {
  notes: NoteEntity[];
  tasks: TaskEntity[];
  onOpenNote: (noteId: number) => void;
  onEditTask: (task: TaskEntity) => void;
}

export const KanbanBoard: React.FC<KanbanBoardProps> = ({
  notes,
  tasks,
  onOpenNote,
  onEditTask,
}) => {
  const [dragOverColumn, setDragOverColumn] = useState<TaskStatus | null>(null);

  // Map notes by ID for quick parent lookup
  const noteById = new Map<number, NoteEntity>();
  for (const n of notes) {
    if (n.id != null) noteById.set(n.id, n);
  }

  // Active tasks: not deleted, and parent note is not archived
  const activeTasks = tasks.filter((t) => {
    if (t.deleted) return false;
    if (t.sourceNoteId != null) {
      const parent = noteById.get(t.sourceNoteId);
      if (!parent || parent.archived || parent.deleted) return false;
    }
    return true;
  });

  // Buckets for columns
  const openTasks = activeTasks.filter((t) => taskStatus(t) === 'OPEN');
  const deferredTasks = activeTasks.filter((t) => taskStatus(t) === 'DEFERRED');
  const completedTasks = activeTasks.filter((t) => taskStatus(t) === 'COMPLETED');

  // Helper to group a column's tasks by note
  const groupTasksByNote = (colTasks: TaskEntity[]) => {
    const grouped = new Map<number | 'loose', TaskEntity[]>();
    for (const t of colTasks) {
      const key = t.sourceNoteId != null && noteById.has(t.sourceNoteId) ? t.sourceNoteId : 'loose';
      if (!grouped.has(key)) grouped.set(key, []);
      grouped.get(key)!.push(t);
    }

    // Sort tasks in each group by createdAt asc
    for (const arr of grouped.values()) {
      arr.sort((a, b) => a.createdAt - b.createdAt);
    }

    // Convert to entries sorted by note createdAt desc, loose items last
    const entries: { note: NoteEntity | null; tasks: TaskEntity[] }[] = [];
    const noteKeys = Array.from(grouped.keys()).filter((k): k is number => k !== 'loose');
    noteKeys.sort((a, b) => (noteById.get(b)?.createdAt || 0) - (noteById.get(a)?.createdAt || 0));

    for (const k of noteKeys) {
      entries.push({ note: noteById.get(k) || null, tasks: grouped.get(k)! });
    }
    if (grouped.has('loose')) {
      entries.push({ note: null, tasks: grouped.get('loose')! });
    }

    return entries;
  };

  const handleDrop = async (e: React.DragEvent, targetStatus: TaskStatus) => {
    e.preventDefault();
    setDragOverColumn(null);
    const taskIdStr = e.dataTransfer.getData('text/plain');
    if (!taskIdStr) return;
    const taskId = Number(taskIdStr);
    if (!isNaN(taskId)) {
      await setTaskStatus(taskId, targetStatus);
    }
  };

  const renderColumn = (title: string, status: TaskStatus, colTasks: TaskEntity[]) => {
    const groups = groupTasksByNote(colTasks);
    const isDragOver = dragOverColumn === status;

    return (
      <div
        onDragOver={(e) => {
          e.preventDefault();
          setDragOverColumn(status);
        }}
        onDragLeave={() => {
          if (dragOverColumn === status) setDragOverColumn(null);
        }}
        onDrop={(e) => handleDrop(e, status)}
        className={`flex-1 flex flex-col min-w-0 rounded-2xl border transition-all ${
          isDragOver
            ? 'border-rust bg-rust-muted/40'
            : 'border-ink-hairline/60 bg-paper'
        } p-3.5 min-h-[500px]`}
      >
        {/* Column Sticky Header */}
        <div className="flex items-center justify-between pb-3 mb-3 border-b border-ink-hairline/60">
          <span className="font-mono text-label text-ink font-semibold">
            {title}
          </span>
          <span className="font-mono text-micro text-ink-faint px-2 py-0.5 rounded-full bg-ink/[0.04]">
            {colTasks.length}
          </span>
        </div>

        {/* Column Task Cards */}
        <div className="flex-1 overflow-y-auto space-y-3 pr-1">
          {groups.length === 0 ? (
            <div className="h-32 flex items-center justify-center border border-dashed border-ink-hairline/50 rounded-xl">
              <span className="font-mono text-micro text-ink-faint">No tasks</span>
            </div>
          ) : (
            groups.map((g, idx) => (
              <NoteTaskGroup
                key={g.note?.id ?? `loose-${idx}`}
                note={g.note}
                tasks={g.tasks}
                onOpenNote={onOpenNote}
                onEditTask={onEditTask}
                draggable={true}
              />
            ))
          )}
        </div>
      </div>
    );
  };

  // Mobile flat list data (Open + Deferred, grouped by note)
  const mobileTasks = activeTasks.filter((t) => !t.done);
  const mobileGroups = groupTasksByNote(mobileTasks);

  return (
    <div className="w-full max-w-[1600px] mx-auto px-6 lg:px-10 py-6 animate-fade-in">
      {/* Desktop 3-Column Kanban Board (lg:) */}
      <div className="hidden lg:grid grid-cols-3 gap-6">
        {renderColumn('Open', 'OPEN', openTasks)}
        {renderColumn('Deferred', 'DEFERRED', deferredTasks)}
        {renderColumn('Completed', 'COMPLETED', completedTasks)}
      </div>

      {/* Mobile Grouped List (<lg) */}
      <div className="lg:hidden space-y-4">
        <div className="flex items-center justify-between pb-2 border-b border-ink-hairline">
          <span className="font-mono text-label text-ink font-semibold">Active tasks</span>
          <span className="font-mono text-micro text-ink-faint">
            {openTasks.length} open {deferredTasks.length > 0 ? `· ${deferredTasks.length} deferred` : ''}
          </span>
        </div>

        {mobileGroups.length === 0 ? (
          <div className="py-12 text-center">
            <p className="font-serif text-secondary text-ink-muted">No open tasks right now.</p>
          </div>
        ) : (
          mobileGroups.map((g, idx) => (
            <NoteTaskGroup
              key={g.note?.id ?? `mobile-loose-${idx}`}
              note={g.note}
              tasks={g.tasks}
              onOpenNote={onOpenNote}
              onEditTask={onEditTask}
              draggable={false}
            />
          ))
        )}
      </div>
    </div>
  );
};

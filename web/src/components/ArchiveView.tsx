import React from 'react';
import { NoteEntity, TaskEntity } from '../db/db';
import { ArchiveRestore, Archive } from 'lucide-react';

interface ArchiveViewProps {
  notes: NoteEntity[];
  tasks: TaskEntity[];
  onOpenNote: (noteId: number) => void;
  onUnarchive: (noteId: number) => void;
}

export const ArchiveView: React.FC<ArchiveViewProps> = ({
  notes,
  tasks,
  onOpenNote,
  onUnarchive,
}) => {
  // Count completed tasks per note
  const completedByNote = new Map<number, number>();
  for (const t of tasks) {
    if (t.sourceNoteId != null && t.done && !t.deleted) {
      completedByNote.set(t.sourceNoteId, (completedByNote.get(t.sourceNoteId) || 0) + 1);
    }
  }

  // Sort notes by updatedAt desc
  const sorted = [...notes].sort((a, b) => b.updatedAt - a.updatedAt);

  if (sorted.length === 0) {
    return (
      <div className="w-full max-w-2xl mx-auto px-6 py-20 text-center animate-fade-in">
        <div className="w-12 h-12 rounded-full border border-ink-hairline mx-auto mb-4 flex items-center justify-center text-ink-faint">
          <Archive className="w-5 h-5 stroke-[1.5]" />
        </div>
        <h3 className="font-serif text-title text-ink mb-2">Nothing archived yet</h3>
        <p className="font-serif text-secondary text-ink-muted max-w-md mx-auto">
          Finish every task in a note — or manually archive one from the note view — and it lands here.
        </p>
      </div>
    );
  }

  return (
    <div className="w-full max-w-[1600px] mx-auto px-6 lg:px-10 py-8 space-y-4 animate-fade-in">
      <div className="flex items-center justify-between pb-2 border-b border-ink-hairline">
        <span className="font-mono text-label text-ink font-semibold">Archived notes</span>
        <span className="font-mono text-micro text-ink-faint">
          {sorted.length} {sorted.length === 1 ? 'note' : 'notes'}
        </span>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {sorted.map((note) => {
          const completedCount = note.id != null ? completedByNote.get(note.id) || 0 : 0;
          const snippet = (note.body || '').replace(/\s+/g, ' ').trim();

          return (
            <div
              key={note.id ?? note.uid}
              onClick={() => note.id != null && onOpenNote(note.id)}
              className="p-4 rounded-xl border border-ink-hairline/80 bg-paper-card hover:border-ink-muted transition-all cursor-pointer flex flex-col justify-between gap-3 group"
            >
              <div>
                <div className="flex items-start justify-between gap-2">
                  <h4 className="font-serif text-item font-medium text-ink group-hover:text-rust transition-colors line-clamp-2">
                    {note.title || 'Untitled note'}
                  </h4>
                </div>

                {snippet && (
                  <p className="font-serif text-xs text-ink-muted mt-1.5 line-clamp-3 leading-relaxed">
                    {snippet}
                  </p>
                )}
              </div>

              <div className="pt-2 border-t border-ink-hairline flex items-center justify-between">
                <span className="font-mono text-micro text-ink-faint">
                  {completedCount > 0 ? `${completedCount} done · archived` : 'Archived'}
                </span>

                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    if (note.id != null) onUnarchive(note.id);
                  }}
                  className="flex items-center gap-1 font-mono text-micro font-medium text-ink-muted hover:text-rust px-2 py-1 rounded hover:bg-ink/[0.04] transition-colors"
                  title="Restore note to stream"
                >
                  <ArchiveRestore className="w-3.5 h-3.5" />
                  <span>Unarchive</span>
                </button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

import React from 'react';
import { NoteEntity } from '../db/db';
import { stripMarkdown } from '../lib/md';
import { Plus } from 'lucide-react';

interface ShelfViewProps {
  notes: NoteEntity[]; // shelf notes only
  onOpenNote: (id: number) => void;
  onNewShelfNote: () => void;
}

export const ShelfView: React.FC<ShelfViewProps> = ({ notes, onOpenNote, onNewShelfNote }) => {
  const sorted = [...notes].sort((a, b) => b.updatedAt - a.updatedAt);

  return (
    <div className="max-w-2xl mx-auto px-6 py-6 space-y-4">
      <div className="flex items-center justify-between">
        <span className="font-mono text-label text-ink-faint uppercase">The Shelf · {sorted.length}</span>
        <button
          onClick={onNewShelfNote}
          className="flex items-center gap-1.5 font-mono text-label text-ink-muted hover:text-ink"
        >
          <Plus className="w-3.5 h-3.5" />
          <span>NEW</span>
        </button>
      </div>

      {sorted.length === 0 ? (
        <p className="font-serif text-secondary text-ink-faint py-16 text-center">
          Nothing on the shelf yet. Long notes you write or dictate land here.
        </p>
      ) : (
        sorted.map((n) => {
          const excerpt = stripMarkdown(n.body);
          const date = new Date(n.updatedAt).toLocaleDateString([], { month: 'short', day: 'numeric' });
          return (
            <button
              key={n.id}
              onClick={() => onOpenNote(n.id!)}
              className="w-full text-left p-5 rounded-2xl border border-ink-hairline bg-paper-card hover:shadow-md transition-all"
            >
              <h3 className="font-serif text-note-title text-ink mb-2">{n.title || 'Untitled note'}</h3>
              {excerpt && <p className="font-serif text-secondary text-ink-muted line-clamp-3">{excerpt}</p>}
              <div className="font-mono text-meta text-ink-faint mt-3">{date}</div>
            </button>
          );
        })
      )}
    </div>
  );
};

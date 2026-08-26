import React, { useState } from 'react';
import { db, SettingsEntity } from '../db/db';
import { recallQuery } from '../ai/groq';
import { X, Search, Sparkles } from 'lucide-react';

interface RecallModalProps {
  settings: SettingsEntity;
  onClose: () => void;
}

export const RecallModal: React.FC<RecallModalProps> = ({
  settings,
  onClose,
}) => {
  const [query, setQuery] = useState('');
  const [response, setResponse] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!query.trim() || isLoading) return;

    if (!settings.apiKey) {
      setResponse('Please configure your Groq / OpenAI API key in Settings to use Recall.');
      return;
    }

    setIsLoading(true);
    try {
      const allNotes = await db.notes.toArray();
      const activeNotes = allNotes.filter((n) => !n.deleted);

      const notesContext = activeNotes
        .map(
          (n) =>
            `Title: ${n.title}\nDate: ${new Date(n.createdAt).toLocaleDateString()}\nContent: ${
              n.body
            }\n---`
        )
        .join('\n\n');

      const answer = await recallQuery(
        query.trim(),
        notesContext,
        settings.apiKey,
        settings.baseUrl,
        settings.model
      );
      setResponse(answer);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      setResponse(`Error recalling thoughts: ${msg}`);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-ink/60 backdrop-blur-md animate-fade-in">
      <div className="bg-paper border border-ink-hairline rounded-3xl p-7 max-w-lg w-full shadow-2xl space-y-6">
        {/* Top Bar */}
        <div className="flex items-center justify-between">
          <span className="font-mono text-label text-rust uppercase flex items-center gap-1.5">
            <Sparkles className="w-3.5 h-3.5" />
            HARK RECALL
          </span>
          <button
            onClick={onClose}
            className="p-1 rounded-full text-ink-faint hover:text-ink transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSearch} className="flex gap-2">
          <div className="relative flex-1">
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Ask anything across your notes…"
              autoFocus
              className="w-full pl-9 pr-3.5 py-3 rounded-xl border border-ink-hairline bg-paper-card text-secondary font-serif text-ink placeholder:text-ink-faint focus:outline-none focus:border-ink"
            />
            <Search className="w-4 h-4 text-ink-faint absolute left-3 top-3.5" />
          </div>
          <button
            type="submit"
            disabled={!query.trim() || isLoading}
            className="px-5 py-3 rounded-xl bg-ink text-paper font-mono text-label hover:opacity-90 disabled:opacity-40"
          >
            {isLoading ? 'SEARCHING…' : 'ASK'}
          </button>
        </form>

        {response && (
          <div className="p-5 rounded-2xl bg-paper-card border border-ink-hairline space-y-2">
            <div className="font-mono text-label text-ink-faint uppercase">
              ANSWER FROM NOTES
            </div>
            <p className="font-serif text-body text-ink whitespace-pre-wrap">
              {response}
            </p>
          </div>
        )}
      </div>
    </div>
  );
};

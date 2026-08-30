import React from 'react';
import { Search, Settings, Grid, AlignJustify, Library } from 'lucide-react';

export type FilterTab = 'ALL' | 'OPEN' | 'NOTES' | 'ARCHIVE';

interface HeaderProps {
  openCount: number;
  deferredCount?: number;
  activeFilter: FilterTab;
  onFilterChange: (f: FilterTab) => void;
  viewMode: 'STREAM' | 'GRID';
  onToggleViewMode: () => void;
  onOpenRecall: () => void;
  onOpenSettings: () => void;
  isConfigured: boolean;
  view: 'stream' | 'shelf';
  onToggleShelf: () => void;
  userName?: string;
  onNavigateLanding?: () => void;
}

export const Header: React.FC<HeaderProps> = ({
  openCount,
  deferredCount = 0,
  activeFilter,
  onFilterChange,
  viewMode,
  onToggleViewMode,
  onOpenRecall,
  onOpenSettings,
  isConfigured,
  view,
  onToggleShelf,
  userName,
  onNavigateLanding,
}) => {
  const today = new Date();
  const dateStr = today.toLocaleDateString('en-US', {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
  });

  const hour = today.getHours();
  const timeGreeting = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  const greeting = userName ? `${timeGreeting}, ${userName}` : timeGreeting;

  const tabLabels: Record<FilterTab, string> = {
    ALL: 'All',
    OPEN: 'Open',
    NOTES: 'Notes',
    ARCHIVE: 'Archive',
  };

  return (
    <header className="w-full px-6 lg:px-10 pt-6 pb-4 border-b border-ink-hairline bg-paper/90 backdrop-blur-md sticky top-0 z-20">
      <div className="w-full max-w-[1600px] mx-auto flex flex-col gap-4">
        {/* Top Row: Title, Date Meta, and Actions */}
        <div className="flex items-baseline justify-between">
          <div className="flex items-baseline gap-3 flex-wrap">
            <h1
              onClick={onNavigateLanding}
              className={`text-title font-serif text-ink ${onNavigateLanding ? 'cursor-pointer hover:text-rust transition-colors' : ''}`}
              title={onNavigateLanding ? 'Go to Home / Overview' : undefined}
            >
              Hark
            </h1>
            <span className="font-mono text-meta text-ink-faint">
              {dateStr} · {greeting} · {openCount} open{deferredCount > 0 ? ` · ${deferredCount} deferred` : ''}
            </span>
          </div>

          <div className="flex items-center gap-4 text-ink-muted">
            {/* Toggle between Stream and Shelf — label shows where you'll go */}
            <button
              onClick={onToggleShelf}
              className="flex items-center gap-1.5 px-2.5 py-1 rounded-full hover:bg-ink-hairline transition-colors"
              title={view === 'shelf' ? 'Back to the Stream' : 'The Shelf — long notes'}
            >
              {view === 'shelf' ? <AlignJustify className="w-4 h-4" /> : <Library className="w-4 h-4" />}
              <span className="font-mono text-label font-medium">{view === 'shelf' ? 'Stream' : 'Shelf'}</span>
            </button>

            {/* View Mode Toggle (Stream vs Grid) — stream only */}
            {view === 'stream' && (
              <button
                onClick={onToggleViewMode}
                className="p-1.5 rounded-full hover:bg-ink-hairline transition-colors"
                title={viewMode === 'STREAM' ? 'Switch to Grid View' : 'Switch to Stream View'}
              >
                {viewMode === 'STREAM' ? <Grid className="w-4 h-4" /> : <AlignJustify className="w-4 h-4" />}
              </button>
            )}

            {/* Recall Semantic Search */}
            <button
              onClick={onOpenRecall}
              className="p-1.5 rounded-full hover:bg-ink-hairline transition-colors"
              title="Recall Notes (Cmd+K)"
            >
              <Search className="w-4 h-4" />
            </button>

            {/* Settings */}
            <button
              onClick={onOpenSettings}
              className="p-1.5 rounded-full hover:bg-ink-hairline transition-colors relative"
              title="Settings"
            >
              <Settings className="w-4 h-4" />
              {!isConfigured && (
                <span className="absolute top-1 right-1 w-2 h-2 rounded-full bg-rust animate-pulse" />
              )}
            </button>
          </div>
        </div>

        {/* API Key Banner if not set */}
        {!isConfigured && (
          <div
            onClick={onOpenSettings}
            className="cursor-pointer px-3.5 py-2.5 rounded-lg bg-rust-muted text-rust flex items-center justify-between gap-3 hover:opacity-90 transition-opacity"
          >
            <span className="font-serif text-secondary">Set your Groq / OpenAI API key to enable AI voice tidying →</span>
            <span className="font-mono text-label shrink-0 font-medium">Configure</span>
          </div>
        )}

        {/* Stream filters, or the Shelf label */}
        {view === 'stream' ? (
          <div className="flex gap-5 font-mono text-label text-ink-faint">
            {(['ALL', 'OPEN', 'NOTES', 'ARCHIVE'] as const).map((tab) => {
              const active = activeFilter === tab;
              return (
                <button
                  key={tab}
                  onClick={() => onFilterChange(tab)}
                  className={`pb-1 transition-all border-b font-medium ${
                    active ? 'border-ink text-ink' : 'border-transparent hover:text-ink-muted'
                  }`}
                >
                  {tabLabels[tab]}
                </button>
              );
            })}
          </div>
        ) : (
          <div className="font-mono text-label text-rust font-medium">The Shelf</div>
        )}
      </div>
    </header>
  );
};

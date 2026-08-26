import React, { useState } from 'react';
import { X, Plus, FileText } from 'lucide-react';

interface FloatingCaptureProps {
  onWrite: () => void;
  onTalk: () => void;
  onShelf: () => void;
}

export const FloatingCapture: React.FC<FloatingCaptureProps> = ({
  onWrite,
  onTalk,
  onShelf,
}) => {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="fixed bottom-6 right-6 z-30 flex items-center justify-end">
      {!expanded ? (
        // Collapsed FAB: Unicode ✎ (U+270E) glyph on ink circle
        <button
          onClick={() => setExpanded(true)}
          className="w-14 h-14 rounded-full bg-ink text-paper shadow-xl hover:scale-105 active:scale-95 transition-all flex items-center justify-center border border-ink-hairline"
          title="Capture Note or Voice"
        >
          <span className="font-serif text-2xl text-paper select-none leading-none">✎</span>
        </button>
      ) : (
        // Expanded Drawer: Slides from right to left
        <div className="flex items-center gap-2 p-1.5 rounded-full bg-paper/95 backdrop-blur-md border border-ink-hairline shadow-2xl animate-slide-in-right">
          {/* Collapse button */}
          <button
            onClick={() => setExpanded(false)}
            className="w-11 h-11 rounded-full border border-ink-hairline flex items-center justify-center text-ink-muted hover:text-ink transition-colors"
          >
            <X className="w-4 h-4" />
          </button>

          {/* Write */}
          <button
            onClick={() => {
              setExpanded(false);
              onWrite();
            }}
            className="px-5 h-11 rounded-full border border-ink-hairline bg-paper-card text-ink font-mono text-xs font-semibold tracking-wider hover:opacity-90 transition-opacity flex items-center gap-1.5"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>WRITE</span>
          </button>

          {/* Shelf note (full-screen long-form) */}
          <button
            onClick={() => {
              setExpanded(false);
              onShelf();
            }}
            className="px-5 h-11 rounded-full border border-ink-hairline bg-paper-card text-ink font-mono text-xs font-semibold tracking-wider hover:opacity-90 transition-opacity flex items-center gap-1.5"
          >
            <FileText className="w-3.5 h-3.5" />
            <span>SHELF</span>
          </button>

          {/* Talk */}
          <button
            onClick={() => {
              setExpanded(false);
              onTalk();
            }}
            className="px-5 h-11 rounded-full bg-ink text-paper font-mono text-xs font-semibold tracking-wider hover:opacity-90 transition-opacity flex items-center gap-2"
          >
            {/* 3 nib bars animation */}
            <span className="flex items-center gap-0.5">
              <span className="w-0.5 h-3 bg-rust rounded-full animate-pulse" />
              <span className="w-0.5 h-2 bg-rust rounded-full animate-pulse delay-75" />
              <span className="w-0.5 h-2.5 bg-rust rounded-full animate-pulse delay-150" />
            </span>
            <span>TALK</span>
          </button>
        </div>
      )}
    </div>
  );
};

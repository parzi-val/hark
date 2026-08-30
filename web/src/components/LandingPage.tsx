import React from 'react';
import { ArrowRight, Smartphone, Mic, BookOpen, ShieldCheck } from 'lucide-react';

interface LandingPageProps {
  onEnterApp: () => void;
}

export const LandingPage: React.FC<LandingPageProps> = ({ onEnterApp }) => {
  const hilbertPath =
    'M6,6 L6,11.4 L11.4,11.4 L11.4,6 L16.8,6 L22.2,6 L22.2,11.4 L16.8,11.4 L16.8,16.8 L22.2,16.8 L22.2,22.2 L16.8,22.2 L11.4,22.2 L11.4,16.8 L6,16.8 L6,22.2 L6,27.6 L11.4,27.6 L11.4,33 L6,33 L6,38.4 L6,43.8 L11.4,43.8 L11.4,38.4 L16.8,38.4 L16.8,43.8 L22.2,43.8 L22.2,38.4 L22.2,33 L16.8,33 L16.8,27.6 L22.2,27.6 L27.6,27.6 L33,27.6 L33,33 L27.6,33 L27.6,38.4 L27.6,43.8 L33,43.8 L33,38.4 L38.4,38.4 L38.4,43.8 L43.8,43.8 L43.8,38.4 L43.8,33 L38.4,33 L38.4,27.6 L43.8,27.6 L43.8,22.2 L43.8,16.8 L38.4,16.8 L38.4,22.2 L33,22.2 L27.6,22.2 L27.6,16.8 L33,16.8 L33,11.4 L27.6,11.4 L27.6,6 L33,6 L38.4,6 L38.4,11.4 L43.8,11.4 L43.8,6';

  return (
    <div className="min-h-screen bg-paper flex flex-col text-ink selection:bg-rust selection:text-white">
      {/* Top Navigation */}
      <header className="w-full max-w-5xl mx-auto px-6 py-6 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <svg viewBox="0 0 48 48" className="w-7 h-7 text-rust">
            <path d={hilbertPath} fill="none" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          <span className="font-serif font-bold text-2xl tracking-tight text-ink">Hark</span>
        </div>

        <div className="flex items-center gap-4">
          <a
            href="https://github.com/parzi-val/hark"
            target="_blank"
            rel="noreferrer"
            className="flex items-center gap-1.5 font-mono text-xs text-ink-muted hover:text-ink transition-colors font-medium"
          >
            <svg viewBox="0 0 24 24" className="w-4 h-4 fill-current">
              <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z" />
            </svg>
            <span className="hidden sm:inline">GitHub</span>
          </a>
          <button
            onClick={onEnterApp}
            className="px-5 py-2 rounded-full bg-ink text-paper font-mono text-xs font-semibold hover:opacity-90 transition-opacity"
          >
            Open app
          </button>
        </div>
      </header>

      {/* Hero Section */}
      <main className="flex-1 max-w-4xl mx-auto px-6 pt-12 pb-20 flex flex-col items-center text-center">
        {/* Animated Hilbert Curve Badge */}
        <div className="mb-8 p-4 rounded-3xl bg-paper-raised border border-ink-hairline shadow-sm">
          <svg viewBox="0 0 48 48" className="w-20 h-20 text-rust animate-pulse">
            <path d={hilbertPath} fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </div>

        <span className="font-mono text-xs text-rust font-semibold mb-4">
          Voice-first thinking space
        </span>

        <h1 className="font-serif text-4xl sm:text-5xl md:text-6xl font-bold tracking-tight text-ink max-w-3xl leading-[1.15]">
          Speak your mind. We’ll shape the thought.
        </h1>

        <p className="mt-6 font-serif text-base sm:text-lg text-ink-muted max-w-2xl leading-relaxed">
          Hark tidies raw stream-of-consciousness voice recordings into structured Markdown notes and extracted checklists. Zero friction, offline-ready, and private.
        </p>

        {/* Primary CTAs */}
        <div className="mt-10 flex flex-col sm:flex-row items-center gap-4 w-full justify-center max-w-md">
          <button
            onClick={onEnterApp}
            className="w-full sm:w-auto px-8 py-3.5 rounded-full bg-ink text-paper font-mono text-xs font-semibold hover:opacity-90 transition-opacity flex items-center justify-center gap-2 shadow-md"
          >
            Continue in web
            <ArrowRight className="w-4 h-4" />
          </button>

          <a
            href="https://github.com/parzi-val/hark"
            target="_blank"
            rel="noreferrer"
            className="w-full sm:w-auto px-7 py-3.5 rounded-full border border-ink-hairline text-ink font-mono text-xs font-semibold hover:border-ink transition-colors flex items-center justify-center gap-2"
          >
            <Smartphone className="w-4 h-4 text-ink-muted" />
            Download for Android
          </a>
        </div>

        {/* Feature Grid */}
        <div className="mt-24 grid grid-cols-1 md:grid-cols-3 gap-6 w-full text-left">
          <div className="p-6 rounded-2xl bg-paper-raised border border-ink-hairline space-y-3">
            <div className="w-9 h-9 rounded-xl bg-paper flex items-center justify-center text-rust border border-ink-hairline">
              <Mic className="w-4 h-4" />
            </div>
            <h3 className="font-serif font-bold text-lg text-ink">Voice & Markdown</h3>
            <p className="font-serif text-xs text-ink-muted leading-relaxed">
              Capture messy thoughts on the go. Hark transcribes, formats with headers and bold key points, and extracts tasks without losing your original words.
            </p>
          </div>

          <div className="p-6 rounded-2xl bg-paper-raised border border-ink-hairline space-y-3">
            <div className="w-9 h-9 rounded-xl bg-paper flex items-center justify-center text-rust border border-ink-hairline">
              <BookOpen className="w-4 h-4" />
            </div>
            <h3 className="font-serif font-bold text-lg text-ink">The River & The Shelf</h3>
            <p className="font-serif text-xs text-ink-muted leading-relaxed">
              Short thoughts flow naturally down the daily Stream. Essays, reading notes, and long pieces rest quietly on The Shelf, accompanied by a daily lexicon word (λέξις).
            </p>
          </div>

          <div className="p-6 rounded-2xl bg-paper-raised border border-ink-hairline space-y-3">
            <div className="w-9 h-9 rounded-xl bg-paper flex items-center justify-center text-rust border border-ink-hairline">
              <ShieldCheck className="w-4 h-4" />
            </div>
            <h3 className="font-serif font-bold text-lg text-ink">Private Cloud Sync</h3>
            <p className="font-serif text-xs text-ink-muted leading-relaxed">
              No central accounts or surveillance. Your notes stay on your device and sync seamlessly through your own private Google Drive folder with live polling.
            </p>
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer className="w-full border-t border-ink-hairline py-8 text-center font-mono text-[11px] text-ink-faint">
        <p>Hark · Private, voice-first cognitive companion</p>
      </footer>
    </div>
  );
};

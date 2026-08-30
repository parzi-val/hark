import React, { useState } from 'react';
import { db, SettingsEntity } from '../db/db';
import { signIn, setSyncEnabled, pullSettingsIfFresh, firstSyncAsync, isSyncEnabled } from '../sync/sync';
import { Cloud, Check, ArrowRight } from 'lucide-react';

interface OnboardingModalProps {
  settings: SettingsEntity;
  onFinish: () => void;
}

export const OnboardingModal: React.FC<OnboardingModalProps> = ({ settings, onFinish }) => {
  const [step, setStep] = useState(0);
  const [userName, setUserName] = useState(settings.userName || '');
  const [apiKey, setApiKey] = useState(settings.apiKey || '');
  const [synced, setSynced] = useState(isSyncEnabled());
  const [syncBusy, setSyncBusy] = useState(false);

  const hilbertPath =
    'M6,6 L6,11.4 L11.4,11.4 L11.4,6 L16.8,6 L22.2,6 L22.2,11.4 L16.8,11.4 L16.8,16.8 L22.2,16.8 L22.2,22.2 L16.8,22.2 L11.4,22.2 L11.4,16.8 L6,16.8 L6,22.2 L6,27.6 L11.4,27.6 L11.4,33 L6,33 L6,38.4 L6,43.8 L11.4,43.8 L11.4,38.4 L16.8,38.4 L16.8,43.8 L22.2,43.8 L22.2,38.4 L22.2,33 L16.8,33 L16.8,27.6 L22.2,27.6 L27.6,27.6 L33,27.6 L33,33 L27.6,33 L27.6,38.4 L27.6,43.8 L33,43.8 L33,38.4 L38.4,38.4 L38.4,43.8 L43.8,43.8 L43.8,38.4 L43.8,33 L38.4,33 L38.4,27.6 L43.8,27.6 L43.8,22.2 L43.8,16.8 L38.4,16.8 L38.4,22.2 L33,22.2 L27.6,22.2 L27.6,16.8 L33,16.8 L33,11.4 L27.6,11.4 L27.6,6 L33,6 L38.4,6 L38.4,11.4 L43.8,11.4 L43.8,6';

  const handleSignIn = async () => {
    setSyncBusy(true);
    try {
      await signIn();
      setSyncEnabled(true);
      setSynced(true);
      await pullSettingsIfFresh();
      const s = await db.settings.get(1);
      if (s?.userName) setUserName(s.userName);
      if (s?.apiKey) setApiKey(s.apiKey);
      // Notes sync is deferred to "Enter Hark" (firstSyncAsync) so it shows the entry loader.
    } catch {
      // ignore
    } finally {
      setSyncBusy(false);
    }
  };

  const handleComplete = async () => {
    await db.settings.update(1, {
      userName: userName.trim(),
      apiKey: apiKey.trim(),
      hasCompletedOnboarding: true,
    });
    if (isSyncEnabled()) firstSyncAsync();
    onFinish();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-ink/70 backdrop-blur-md animate-fade-in text-ink">
      <div className="bg-paper border border-ink-hairline rounded-3xl max-w-lg w-full shadow-2xl p-6 sm:p-8 flex flex-col justify-between min-h-[480px]">
        {/* Top bar */}
        <div className="flex items-center justify-between">
          <span className="font-mono text-xs text-ink font-semibold">
            Welcome to Hark
          </span>
          {step < 2 && (
            <button
              type="button"
              onClick={() => setStep(2)}
              className="font-mono text-xs text-ink-muted hover:text-ink font-medium"
            >
              Skip
            </button>
          )}
        </div>

        {/* Step 0: Capture & Shape */}
        {step === 0 && (
          <div className="my-auto flex flex-col items-center text-center space-y-5">
            <svg viewBox="0 0 48 48" className="w-20 h-20 text-rust">
              <path d={hilbertPath} fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
            <h2 className="font-serif font-bold text-2xl sm:text-3xl text-ink leading-snug">
              Speak your mind.<br />We’ll shape the thought.
            </h2>
            <p className="font-serif text-sm text-ink-muted leading-relaxed max-w-sm">
              Hark transforms raw spoken thoughts into clean, structured Markdown and extracts action items with zero friction.
            </p>
          </div>
        )}

        {/* Step 1: River & Shelf */}
        {step === 1 && (
          <div className="my-auto flex flex-col items-center text-center space-y-5">
            <div className="flex items-center gap-3">
              <div className="px-4 py-2.5 rounded-xl bg-paper-raised border border-ink-hairline font-mono text-xs font-semibold text-ink">
                The Stream
              </div>
              <span className="font-serif text-rust text-lg">→</span>
              <div className="px-4 py-2.5 rounded-xl bg-paper-raised border border-ink-hairline font-mono text-xs font-semibold text-rust">
                The Shelf
              </div>
            </div>
            <h2 className="font-serif font-bold text-2xl sm:text-3xl text-ink">
              The River & The Shelf
            </h2>
            <p className="font-serif text-sm text-ink-muted leading-relaxed max-w-sm">
              Quick thoughts flow through your daily Stream. Longer writing and reading notes rest on The Shelf, accompanied by a daily vocabulary word (λέξις).
            </p>
          </div>
        )}

        {/* Step 2: Personalize & Optional Connect */}
        {step === 2 && (
          <div className="my-auto space-y-5">
            <div>
              <h2 className="font-serif font-bold text-2xl text-ink">Make it yours.</h2>
              <p className="font-serif text-xs text-ink-muted">Personalize Hark and connect optional cloud sync.</p>
            </div>

            <div className="space-y-1.5">
              <span className="font-mono text-xs text-ink-faint font-semibold">Your name</span>
              <input
                type="text"
                value={userName}
                onChange={(e) => setUserName(e.target.value)}
                placeholder="e.g. Bala"
                className="w-full px-3.5 py-2.5 rounded-xl border border-ink-hairline bg-transparent font-serif text-sm text-ink placeholder:text-ink-faint focus:outline-none focus:border-ink"
              />
            </div>

            <div className="space-y-1.5">
              <span className="font-mono text-xs text-ink-faint font-semibold">Google Drive Sync (optional)</span>
              {!synced ? (
                <button
                  type="button"
                  onClick={handleSignIn}
                  disabled={syncBusy}
                  className="w-full h-11 rounded-xl bg-paper-raised border border-ink-hairline font-mono text-xs font-semibold text-ink hover:border-ink flex items-center justify-center gap-2 disabled:opacity-50"
                >
                  <Cloud className="w-4 h-4 text-rust" />
                  {syncBusy ? 'Connecting…' : 'Connect Google Drive'}
                </button>
              ) : (
                <div className="w-full p-3 rounded-xl bg-paper-raised border border-ink-hairline flex items-center justify-between">
                  <span className="font-serif text-xs text-ink">Connected to Google Drive</span>
                  <span className="font-mono text-xs font-bold text-rust flex items-center gap-1">
                    <Check className="w-3.5 h-3.5" /> Active
                  </span>
                </div>
              )}
            </div>

            <div className="space-y-1.5">
              <span className="font-mono text-xs text-ink-faint font-semibold">Groq / OpenAI API key (optional)</span>
              <input
                type="password"
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                placeholder="gsk_... or sk-..."
                className="w-full px-3.5 py-2.5 rounded-xl border border-ink-hairline bg-transparent font-mono text-xs text-ink placeholder:text-ink-faint focus:outline-none focus:border-ink"
              />
            </div>
          </div>
        )}

        {/* Bottom Bar: Dots & CTA */}
        <div className="pt-6 space-y-4">
          <div className="flex items-center justify-center gap-2">
            {[0, 1, 2].map((i) => (
              <div
                key={i}
                className={`w-2 h-2 rounded-full transition-colors ${
                  step === i ? 'bg-rust' : 'bg-ink-hairline'
                }`}
              />
            ))}
          </div>

          {step < 2 ? (
            <button
              type="button"
              onClick={() => setStep((s) => s + 1)}
              className="w-full h-12 rounded-full bg-ink text-paper font-mono text-xs font-semibold hover:opacity-90 transition-opacity flex items-center justify-center gap-2"
            >
              Continue
              <ArrowRight className="w-4 h-4" />
            </button>
          ) : (
            <button
              type="button"
              onClick={handleComplete}
              className="w-full h-12 rounded-full bg-ink text-paper font-mono text-xs font-semibold hover:opacity-90 transition-opacity flex items-center justify-center gap-2"
            >
              Enter Hark
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

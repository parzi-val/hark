import React, { useState, useEffect } from 'react';
import { SettingsEntity, db, DEFAULT_BASE_URL, DEFAULT_MODEL } from '../db/db';
import {
  signIn,
  signOut,
  isSignedIn,
  scheduleSync,
  isApiKeySynced,
  setApiKeySynced,
  pushSettings,
} from '../sync/sync';
import { X, Cloud, Check, Smartphone } from 'lucide-react';

interface SettingsModalProps {
  settings: SettingsEntity;
  onClose: () => void;
  onSaved: () => void;
}

export const SettingsModal: React.FC<SettingsModalProps> = ({
  settings,
  onClose,
  onSaved,
}) => {
  const [apiKey, setApiKey] = useState(settings.apiKey);
  const [baseUrl, setBaseUrl] = useState(settings.baseUrl);
  const [model, setModel] = useState(settings.model);
  const [userName, setUserName] = useState(settings.userName || '');
  const [themeMode, setThemeMode] = useState<SettingsEntity['themeMode']>(settings.themeMode);
  const [showApiKey, setShowApiKey] = useState(false);
  const [saved, setSaved] = useState(false);

  // Google Drive state
  const [synced, setSynced] = useState(isSignedIn());
  const [syncBusy, setSyncBusy] = useState(false);
  const [syncMsg, setSyncMsg] = useState<string | null>(null);
  const [keyOptIn, setKeyOptIn] = useState(isApiKeySynced());

  useEffect(() => {
    setSynced(isSignedIn());
    setKeyOptIn(isApiKeySynced());
  }, []);

  const handleSignIn = async () => {
    setSyncBusy(true);
    setSyncMsg(null);
    try {
      await signIn();
      setSynced(true);
      setSyncMsg('Connected to Google Drive');
    } catch (e: any) {
      setSyncMsg(e?.message || 'Failed to sign in');
    } finally {
      setSyncBusy(false);
    }
  };

  const handleSignOut = () => {
    signOut();
    setSynced(false);
    setSyncMsg('Signed out');
  };

  const toggleKeyOptIn = () => {
    const next = !keyOptIn;
    setKeyOptIn(next);
    setApiKeySynced(next);
  };

  const handleSave = async () => {
    await db.settings.update(1, {
      apiKey: apiKey.trim(),
      baseUrl: baseUrl.trim() || DEFAULT_BASE_URL,
      model: model.trim() || DEFAULT_MODEL,
      userName: userName.trim(),
      themeMode,
    });
    setSaved(true);
    void pushSettings().catch(() => {});
    scheduleSync(0);
    setTimeout(() => {
      onSaved();
      onClose();
    }, 600);
  };

  const applyTheme = (mode: SettingsEntity['themeMode']) => {
    setThemeMode(mode);
    const root = document.documentElement;
    const isDark =
      mode === 'DARK' ||
      (mode === 'SYSTEM' && window.matchMedia('(prefers-color-scheme: dark)').matches);
    if (isDark) root.classList.add('dark');
    else root.classList.remove('dark');
  };

  const inputClass =
    'w-full px-3.5 py-2.5 rounded-lg border border-ink-hairline bg-transparent font-serif text-sm text-ink placeholder:text-ink-faint focus:outline-none focus:border-ink';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-ink/60 backdrop-blur-md animate-fade-in">
      <div className="bg-paper border border-ink-hairline rounded-3xl max-w-md w-full shadow-2xl max-h-[90vh] flex flex-col overflow-hidden">
        {/* Fixed Header */}
        <div className="px-6 py-4 flex items-center justify-between border-b border-ink-hairline bg-paper">
          <span className="font-mono text-xs text-ink font-semibold">Settings</span>
          <button
            onClick={onClose}
            className="p-1 rounded-full text-ink-faint hover:text-ink transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Scrollable Body */}
        <div className="px-6 py-5 overflow-y-auto flex-1 space-y-5">
          {/* User Name */}
          <div className="space-y-1.5">
            <span className="font-mono text-xs text-ink-faint font-semibold">Your name</span>
            <input
              type="text"
              value={userName}
              onChange={(e) => {
                setUserName(e.target.value);
                setSaved(false);
              }}
              placeholder="How should Hark greet you?"
              className={inputClass}
            />
          </div>

          <hr className="border-ink-hairline" />

          {/* App Theme (live) */}
          <div className="space-y-2">
            <span className="font-mono text-xs text-ink-faint font-semibold">App theme</span>
            <div className="grid grid-cols-3 gap-2">
              {(['SYSTEM', 'LIGHT', 'DARK'] as const).map((t) => {
                const active = themeMode === t;
                const labels: Record<string, string> = { SYSTEM: 'System', LIGHT: 'Light', DARK: 'Dark' };
                return (
                  <button
                    key={t}
                    type="button"
                    onClick={() => applyTheme(t)}
                    className={`h-10 rounded-full font-mono text-xs border transition-colors ${
                      active
                        ? 'bg-ink text-paper border-ink font-semibold'
                        : 'bg-transparent text-ink-muted border-ink-hairline hover:border-ink-faint'
                    }`}
                  >
                    {labels[t]}
                  </button>
                );
              })}
            </div>
          </div>

          <hr className="border-ink-hairline" />

          {/* Google Drive Sync */}
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <div className="space-y-0.5">
                <span className="font-mono text-xs text-ink-faint font-semibold block">Google Drive Sync</span>
                <span className="font-serif text-[11px] text-ink-muted block">
                  Syncs across Web and Android
                </span>
              </div>
            </div>

            {!synced ? (
              <button
                type="button"
                onClick={handleSignIn}
                disabled={syncBusy}
                className="w-full h-11 rounded-full bg-ink text-paper font-mono text-xs font-semibold hover:opacity-90 disabled:opacity-50 flex items-center justify-center gap-2"
              >
                <Cloud className="w-4 h-4" />
                {syncBusy ? 'Connecting…' : 'Sign in with Google'}
              </button>
            ) : (
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <span className="font-mono text-[11px] text-ink flex items-center gap-1.5 font-medium">
                    <Check className="w-3.5 h-3.5 text-rust" />
                    Connected · Drive
                  </span>
                  <button
                    type="button"
                    onClick={handleSignOut}
                    className="font-mono text-[11px] text-ink-muted hover:text-ink font-medium"
                  >
                    Sign out
                  </button>
                </div>

                <div className="w-full flex items-center justify-between p-3 rounded-xl bg-paper-card border border-ink-hairline">
                  <div className="space-y-0.5">
                    <span className="font-serif text-xs font-semibold text-ink block">Automatic Cloud Sync</span>
                    <span className="font-serif text-[11px] text-ink-muted block">Syncs live on edit, save, delete & focus</span>
                  </div>
                  <span className="font-mono text-[10.5px] font-bold text-rust">● Active</span>
                </div>

                <button
                  type="button"
                  onClick={toggleKeyOptIn}
                  className="w-full flex items-center justify-between gap-3 p-3 rounded-xl bg-paper-card border border-ink-hairline text-left"
                >
                  <span className="font-serif text-xs text-ink-muted">
                    Also sync my API key
                    <span className="block text-ink-faint">Stored only in your private Drive folder.</span>
                  </span>
                  <span className={`shrink-0 w-9 h-5 rounded-full relative transition-colors ${keyOptIn ? 'bg-rust' : 'bg-ink-hairline'}`}>
                    <span className={`absolute top-0.5 w-4 h-4 rounded-full bg-paper transition-all ${keyOptIn ? 'left-4' : 'left-0.5'}`} />
                  </span>
                </button>
              </div>
            )}

            {syncMsg && <p className="font-serif text-xs text-rust font-semibold">{syncMsg}</p>}
          </div>

          <hr className="border-ink-hairline" />

          {/* AI Configuration */}
          <div className="space-y-4">
            <div className="space-y-1">
              <h2 className="font-serif font-bold text-base text-ink">AI Configuration</h2>
              <p className="font-serif text-xs text-ink-muted leading-relaxed">
                Hark connects directly to any OpenAI-compatible API to tidy your notes and extract tasks. Default provider is Groq for low-latency responses.
              </p>
            </div>

            {/* API Key */}
            <div className="space-y-1.5">
              <span className="font-mono text-xs text-ink-faint font-semibold">API key</span>
              <div className="relative">
                <input
                  type={showApiKey ? 'text' : 'password'}
                  value={apiKey}
                  onChange={(e) => {
                    setApiKey(e.target.value);
                    setSaved(false);
                  }}
                  placeholder="gsk_..."
                  className={`${inputClass} pr-16 font-mono text-xs`}
                />
                <button
                  type="button"
                  onClick={() => setShowApiKey((v) => !v)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 font-mono text-xs text-ink-muted hover:text-ink font-medium"
                >
                  {showApiKey ? 'Hide' : 'Show'}
                </button>
              </div>
              <span className={`font-mono text-[11px] block ${apiKey.trim() ? 'text-ink-faint' : 'text-rust font-medium'}`}>
                Get a free API key at console.groq.com
              </span>
            </div>

            {/* Base URL */}
            <div className="space-y-1.5">
              <span className="font-mono text-xs text-ink-faint font-semibold">Base URL</span>
              <input
                type="text"
                value={baseUrl}
                onChange={(e) => {
                  setBaseUrl(e.target.value);
                  setSaved(false);
                }}
                className={`${inputClass} font-mono text-xs`}
              />
              <span className="font-mono text-[10.5px] text-ink-faint block">Default: {DEFAULT_BASE_URL}</span>
            </div>

            {/* Model */}
            <div className="space-y-1.5">
              <span className="font-mono text-xs text-ink-faint font-semibold">Model</span>
              <input
                type="text"
                value={model}
                onChange={(e) => {
                  setModel(e.target.value);
                  setSaved(false);
                }}
                className={`${inputClass} font-mono text-xs`}
              />
              <span className="font-mono text-[10.5px] text-ink-faint block">Default: {DEFAULT_MODEL}</span>
            </div>

            {saved && (
              <p className="font-serif text-xs text-rust font-semibold">Settings saved successfully.</p>
            )}
          </div>

          {/* PWA install tip */}
          <div className="p-3.5 rounded-xl bg-paper-card border border-ink-hairline font-serif text-xs text-ink-muted space-y-1">
            <div className="font-mono text-[11px] text-ink flex items-center gap-1.5 font-bold">
              <Smartphone className="w-3.5 h-3.5" />
              Install on iPhone / iPad
            </div>
            <p className="leading-relaxed">
              Tap the <strong>Share</strong> button in Safari → tap <strong>"Add to Home Screen"</strong> to run Hark full-screen with offline support and icon badges.
            </p>
          </div>
        </div>

        {/* Fixed Footer */}
        <div className="px-6 py-3.5 border-t border-ink-hairline bg-paper flex items-center justify-end gap-2">
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-full border border-ink-hairline text-ink-muted hover:text-ink font-mono text-xs font-medium"
          >
            Cancel
          </button>
          <button
            onClick={handleSave}
            className="px-6 py-2 rounded-full bg-ink text-paper font-mono text-xs font-semibold hover:opacity-90 transition-opacity"
          >
            Save
          </button>
        </div>
      </div>
    </div>
  );
};

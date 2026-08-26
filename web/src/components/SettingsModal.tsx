import React, { useState } from 'react';
import { SettingsEntity, db, DEFAULT_BASE_URL, DEFAULT_MODEL } from '../db/db';
import { X, Smartphone } from 'lucide-react';

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
  const [baseUrl, setBaseUrl] = useState(settings.baseUrl || DEFAULT_BASE_URL);
  const [model, setModel] = useState(settings.model || DEFAULT_MODEL);
  const [themeMode, setThemeMode] = useState<'SYSTEM' | 'LIGHT' | 'DARK'>(settings.themeMode || 'SYSTEM');
  const [showApiKey, setShowApiKey] = useState(false);
  const [saved, setSaved] = useState(false);

  // Theme is applied live, like the Android screen.
  const applyTheme = async (t: 'SYSTEM' | 'LIGHT' | 'DARK') => {
    setThemeMode(t);
    await db.settings.update(1, { themeMode: t });
  };

  const handleSave = async () => {
    await db.settings.update(1, {
      apiKey: apiKey.trim(),
      baseUrl: baseUrl.trim() || DEFAULT_BASE_URL,
      model: model.trim() || DEFAULT_MODEL,
    });
    setSaved(true);
    onSaved();
  };

  const inputClass =
    'w-full px-3.5 py-2.5 rounded-lg border border-ink-hairline bg-transparent font-serif text-sm text-ink placeholder:text-ink-faint focus:outline-none focus:border-ink';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-ink/60 backdrop-blur-md animate-fade-in">
      <div className="bg-paper border border-ink-hairline rounded-3xl max-w-md w-full shadow-2xl max-h-[90vh] flex flex-col overflow-hidden">
        {/* Fixed Header */}
        <div className="px-6 py-4 flex items-center justify-between border-b border-ink-hairline bg-paper">
          <span className="font-mono text-xs text-ink-faint uppercase font-semibold tracking-wider">SETTINGS</span>
          <button
            onClick={onClose}
            className="p-1 rounded-full text-ink-faint hover:text-ink transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Scrollable Body */}
        <div className="px-6 py-5 overflow-y-auto flex-1 space-y-5">
          {/* App Theme (live) */}
          <div className="space-y-2">
            <span className="font-mono text-xs text-ink-faint uppercase tracking-wider font-semibold">APP THEME</span>
            <div className="grid grid-cols-3 gap-2">
              {(['SYSTEM', 'LIGHT', 'DARK'] as const).map((t) => {
                const active = themeMode === t;
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
                    {t}
                  </button>
                );
              })}
            </div>
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
              <span className="font-mono text-xs text-ink-faint uppercase tracking-wider">API KEY</span>
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
                  className="absolute right-3 top-1/2 -translate-y-1/2 font-mono text-xs text-ink-muted hover:text-ink"
                >
                  {showApiKey ? 'HIDE' : 'SHOW'}
                </button>
              </div>
              <span className={`font-mono text-[10.5px] uppercase tracking-wider block ${apiKey.trim() ? 'text-ink-faint' : 'text-rust font-semibold'}`}>
                Get a free API key at console.groq.com
              </span>
            </div>

            {/* Base URL */}
            <div className="space-y-1.5">
              <span className="font-mono text-xs text-ink-faint uppercase tracking-wider">BASE URL</span>
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
              <span className="font-mono text-xs text-ink-faint uppercase tracking-wider">MODEL</span>
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
            <div className="font-mono text-[10.5px] text-ink uppercase flex items-center gap-1.5 font-bold">
              <Smartphone className="w-3.5 h-3.5" />
              INSTALL ON IPHONE / IPAD
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
            className="px-4 py-2.5 rounded-full border border-ink-hairline text-ink-muted hover:text-ink font-mono text-xs tracking-wider"
          >
            CANCEL
          </button>
          <button
            onClick={handleSave}
            className="px-6 py-2.5 rounded-full bg-ink text-paper font-mono text-xs font-semibold tracking-wider hover:opacity-90 transition-opacity"
          >
            SAVE
          </button>
        </div>
      </div>
    </div>
  );
};

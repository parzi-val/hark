import React, { useState, useEffect, useRef } from 'react';
import { AudioRecorder } from '../audio/recorder';
import { transcribeAudio, processCapture, HarkAction, FocusedNote } from '../ai/groq';
import { recentNoteRefs, applyAction, SHELF_THRESHOLD } from '../db/actions';
import { db, SettingsEntity } from '../db/db';
import { Markdown } from './Markdown';
import { X, Mic } from 'lucide-react';

interface TalkModalProps {
  settings: SettingsEntity;
  onClose: () => void;
  onSaved: (noteId: number) => void;
  focusedNote?: FocusedNote | null;
}

type Status = 'LISTENING' | 'PROCESSING' | 'RESULT' | 'ERROR';

export const TalkModal: React.FC<TalkModalProps> = ({ settings, onClose, onSaved, focusedNote }) => {
  const [status, setStatus] = useState<Status>('LISTENING');
  const [statusText, setStatusText] = useState('Listening to your stream of thought…');
  const [audioLevel, setAudioLevel] = useState(0);
  const [errorMsg, setErrorMsg] = useState('');
  const [extractTasks, setExtractTasks] = useState(true);
  const [checklistOnly, setChecklistOnly] = useState(false);
  const [transcript, setTranscript] = useState('');
  const [pending, setPending] = useState<HarkAction | null>(null);
  const [targetTitle, setTargetTitle] = useState<string | null>(null);
  const recorderRef = useRef<AudioRecorder | null>(null);

  const startRecording = () => {
    const recorder = new AudioRecorder();
    recorderRef.current = recorder;
    setStatus('LISTENING');
    setStatusText('Listening to your stream of thought…');
    recorder.start((lvl) => setAudioLevel(lvl)).catch((err) => {
      setStatus('ERROR');
      setErrorMsg(`Microphone access denied: ${err.message}`);
    });
  };

  useEffect(() => {
    startRecording();
    return () => {
      recorderRef.current?.stop().catch(() => {});
    };
  }, []);

  const handleFinishRecording = async () => {
    if (!recorderRef.current) return;
    setStatus('PROCESSING');
    setStatusText('Transcribing audio with Whisper…');

    try {
      const audioBlob = await recorderRef.current.stop();
      recorderRef.current = null;

      if (!settings.apiKey) {
        const now = Date.now();
        const noteId = await db.notes.add({
          title: 'Voice Note (No API Key)',
          body: 'Audio captured, but no Groq/OpenAI key was configured to transcribe it. Configure your API key in Settings.',
          source: 'VOICE',
          pinnedToWidget: false,
          createdAt: now,
          updatedAt: now,
          deleted: false,
        });
        onSaved(noteId);
        return;
      }

      const text = await transcribeAudio(audioBlob, settings.apiKey, settings.baseUrl);
      if (!text.trim()) {
        setStatus('ERROR');
        setErrorMsg('No speech detected. Please try again.');
        return;
      }
      setTranscript(text);

      setStatusText('Tidying thought & sorting it into your notes…');
      const notes = await recentNoteRefs();
      // Long captures land on the Shelf as prose — don't fragment a ramble into a checklist.
      const willShelf = !focusedNote && !checklistOnly && text.length > SHELF_THRESHOLD;
      const action = await processCapture({
        transcript: text,
        apiKey: settings.apiKey,
        baseUrl: settings.baseUrl,
        model: settings.model,
        extractTasks: checklistOnly || (extractTasks && !willShelf),
        notes,
        focusedNote,
        checklistOnly,
      });

      // Resolve target title for the confirmation line.
      let title: string | null = null;
      if (action.action !== 'create' && action.targetNoteId != null) {
        title =
          focusedNote?.id === action.targetNoteId
            ? focusedNote.title
            : notes.find((n) => n.id === action.targetNoteId)?.title ?? null;
      }
      setTargetTitle(title);
      setPending(action);
      setStatus('RESULT');
    } catch (err: unknown) {
      setStatus('ERROR');
      setErrorMsg(err instanceof Error ? err.message : String(err));
    }
  };

  const handleKeep = async () => {
    if (!pending) return;
    const noteId = await applyAction(pending, transcript, 'VOICE');
    onSaved(noteId);
  };

  const actionHeader = (a: HarkAction): string => {
    if (a.action === 'append') return `→ APPENDING TO "${targetTitle ?? 'a note'}"`;
    if (a.action === 'edit') return `→ EDITING "${targetTitle ?? 'a note'}"`;
    return 'NEW NOTE';
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-ink/60 backdrop-blur-md animate-fade-in">
      <div className="bg-paper border border-ink-hairline rounded-3xl p-8 max-w-md w-full shadow-2xl flex flex-col text-center space-y-6 max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="w-full flex items-center justify-between">
          <span className="font-mono text-label text-rust font-semibold flex items-center gap-1.5">
            {status === 'LISTENING' && <span className="w-2 h-2 rounded-full bg-rust animate-pulse" />}
            {focusedNote ? 'Talk to edit' : 'Talk to Hark'}
          </span>
          <button onClick={onClose} className="p-1 rounded-full text-ink-faint hover:text-ink transition-colors">
            <X className="w-5 h-5" />
          </button>
        </div>

        {status === 'LISTENING' && (
          <>
            <div className="py-4 flex items-center justify-center gap-1.5 h-24">
              {Array.from({ length: 9 }).map((_, i) => {
                const offset = Math.sin((i / 8) * Math.PI);
                const height = Math.max(8, (offset * 0.4 + audioLevel * 0.6) * 56);
                return (
                  <span key={i} style={{ height: `${height}px` }} className="w-1.5 bg-rust rounded-full transition-all duration-75 ease-out" />
                );
              })}
            </div>
            <p className="font-serif text-secondary text-ink-muted">{statusText}</p>
            <div className="flex items-center justify-center gap-4 font-mono text-meta font-medium">
              <button type="button" onClick={() => setChecklistOnly((v) => !v)}>
                <span className={checklistOnly ? 'text-rust font-semibold' : 'text-ink-faint'}>
                  {checklistOnly ? '●' : '○'} Checklist only
                </span>
              </button>
              {!checklistOnly && (
                <button type="button" onClick={() => setExtractTasks((v) => !v)} className="text-ink-muted">
                  Extract tasks: <span className={extractTasks ? 'text-rust font-semibold' : 'text-ink-faint'}>{extractTasks ? 'On' : 'Off'}</span>
                </button>
              )}
            </div>
            <button
              onClick={handleFinishRecording}
              className="w-full py-4 rounded-2xl bg-ink text-paper font-mono text-label font-medium hover:opacity-90 transition-opacity flex items-center justify-center gap-2"
            >
              <Mic className="w-4 h-4 text-rust" />
              <span>Done speaking</span>
            </button>
          </>
        )}

        {status === 'PROCESSING' && (
          <div className="py-10 flex flex-col items-center gap-4">
            <div className="w-8 h-8 border-2 border-rust border-t-transparent rounded-full animate-spin" />
            <p className="font-serif text-secondary text-ink-muted">{statusText}</p>
          </div>
        )}

        {status === 'RESULT' && pending && (
          <>
            <div className="text-left space-y-3 max-h-[46vh] overflow-y-auto">
              <div className="font-mono text-meta text-rust font-semibold">{actionHeader(pending)}</div>
              {pending.action !== 'append' && pending.title && (
                <h2 className="font-serif text-note-title text-ink">{pending.title}</h2>
              )}
              {pending.body.trim() && <Markdown>{pending.body}</Markdown>}
              {pending.tasks.length > 0 && (
                <div className="pt-2 space-y-2">
                  {pending.tasks.map((t, i) => (
                    <div key={i} className="flex items-center gap-2.5">
                      <span className="w-4 h-4 rounded-sm border border-checkbox-border flex-shrink-0" />
                      <span className="font-serif text-item text-ink flex-1">{t.title}</span>
                      {t.dueHint && <span className="font-mono text-meta text-rust font-medium">{t.dueHint}</span>}
                    </div>
                  ))}
                </div>
              )}
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => { setPending(null); startRecording(); }}
                className="flex-1 py-3.5 rounded-2xl border border-ink-hairline text-ink-muted hover:text-ink font-mono text-label font-medium"
              >
                Again
              </button>
              <button
                onClick={handleKeep}
                className="flex-[2] py-3.5 rounded-2xl bg-ink text-paper font-mono text-label font-medium hover:opacity-90 transition-opacity"
              >
                Keep
              </button>
            </div>
          </>
        )}

        {status === 'ERROR' && (
          <>
            <div className="py-4 font-mono text-meta text-rust bg-rust-muted p-3 rounded-xl text-left break-words">{errorMsg}</div>
            <button onClick={onClose} className="w-full py-3 rounded-2xl bg-ink text-paper font-mono text-label font-medium">
              Close
            </button>
          </>
        )}
      </div>
    </div>
  );
};

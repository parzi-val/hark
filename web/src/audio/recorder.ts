export class AudioRecorder {
  private mediaRecorder: MediaRecorder | null = null;
  private audioChunks: Blob[] = [];
  private stream: MediaStream | null = null;
  private audioContext: AudioContext | null = null;
  private analyser: AnalyserNode | null = null;
  private animFrameId: number | null = null;

  async start(onLevel?: (level: number, bars: number[]) => void): Promise<void> {
    this.audioChunks = [];
    this.stream = await navigator.mediaDevices.getUserMedia({ audio: true });

    // Web Audio Analyser for live visualizer wave
    const AudioContextClass = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    this.audioContext = new AudioContextClass();
    const source = this.audioContext.createMediaStreamSource(this.stream);
    this.analyser = this.audioContext.createAnalyser();
    this.analyser.fftSize = 256;
    source.connect(this.analyser);

    if (onLevel) {
      const BAR_COUNT = 28;
      const dataArray = new Uint8Array(this.analyser.frequencyBinCount);
      const updateLevel = () => {
        if (!this.analyser) return;
        this.analyser.getByteFrequencyData(dataArray);

        let sum = 0;
        for (let i = 0; i < dataArray.length; i++) sum += dataArray[i];
        const normalized = Math.min(1, sum / dataArray.length / 80);

        // Per-bar spectrum from the voice-heavy lower ~70% of bins → an expressive wave.
        const usable = Math.floor(dataArray.length * 0.7);
        const step = usable / BAR_COUNT;
        const bars = new Array(BAR_COUNT);
        for (let b = 0; b < BAR_COUNT; b++) {
          const start = Math.floor(b * step);
          const end = Math.max(start + 1, Math.floor((b + 1) * step));
          let s = 0;
          for (let i = start; i < end; i++) s += dataArray[i];
          bars[b] = Math.min(1, s / (end - start) / 150);
        }

        onLevel(normalized, bars);
        this.animFrameId = requestAnimationFrame(updateLevel);
      };
      updateLevel();
    }

    // Determine mimeType for iOS Safari vs Chrome
    let mimeType = 'audio/webm;codecs=opus';
    if (!MediaRecorder.isTypeSupported(mimeType)) {
      if (MediaRecorder.isTypeSupported('audio/mp4')) {
        mimeType = 'audio/mp4';
      } else if (MediaRecorder.isTypeSupported('audio/webm')) {
        mimeType = 'audio/webm';
      } else {
        mimeType = '';
      }
    }

    this.mediaRecorder = mimeType
      ? new MediaRecorder(this.stream, { mimeType })
      : new MediaRecorder(this.stream);

    this.mediaRecorder.ondataavailable = (event) => {
      if (event.data.size > 0) {
        this.audioChunks.push(event.data);
      }
    };

    this.mediaRecorder.start(100);
  }

  stop(): Promise<Blob> {
    return new Promise((resolve, reject) => {
      if (this.animFrameId) {
        cancelAnimationFrame(this.animFrameId);
        this.animFrameId = null;
      }
      if (this.audioContext) {
        this.audioContext.close();
        this.audioContext = null;
      }

      if (!this.mediaRecorder || this.mediaRecorder.state === 'inactive') {
        reject(new Error('Recorder not active'));
        return;
      }

      this.mediaRecorder.onstop = () => {
        const mimeType = this.mediaRecorder?.mimeType || 'audio/webm';
        const audioBlob = new Blob(this.audioChunks, { type: mimeType });
        this.stream?.getTracks().forEach((track) => track.stop());
        resolve(audioBlob);
      };

      this.mediaRecorder.stop();
    });
  }
}

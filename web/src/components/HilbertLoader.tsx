import React from 'react';

// Order-3 Hilbert curve — the Hark mark, animated as a looping trace (mirrors the Android
// HilbertSpinner). Path length ≈ 342, driven by the .hilbert-trace keyframes in index.css.
const HILBERT_PATH =
  'M6,6 L6,11.4 L11.4,11.4 L11.4,6 L16.8,6 L22.2,6 L22.2,11.4 L16.8,11.4 L16.8,16.8 L22.2,16.8 L22.2,22.2 L16.8,22.2 L11.4,22.2 L11.4,16.8 L6,16.8 L6,22.2 L6,27.6 L11.4,27.6 L11.4,33 L6,33 L6,38.4 L6,43.8 L11.4,43.8 L11.4,38.4 L16.8,38.4 L16.8,43.8 L22.2,43.8 L22.2,38.4 L22.2,33 L16.8,33 L16.8,27.6 L22.2,27.6 L27.6,27.6 L33,27.6 L33,33 L27.6,33 L27.6,38.4 L27.6,43.8 L33,43.8 L33,38.4 L38.4,38.4 L38.4,43.8 L43.8,43.8 L43.8,38.4 L43.8,33 L38.4,33 L38.4,27.6 L43.8,27.6 L43.8,22.2 L43.8,16.8 L38.4,16.8 L38.4,22.2 L33,22.2 L27.6,22.2 L27.6,16.8 L33,16.8 L33,11.4 L27.6,11.4 L27.6,6 L33,6 L38.4,6 L38.4,11.4 L43.8,11.4 L43.8,6';

export const HilbertLoader: React.FC<{ className?: string }> = ({ className }) => (
  <svg viewBox="0 0 48 48" className={className ?? 'w-16 h-16 text-rust'} aria-hidden>
    <path
      className="hilbert-trace"
      d={HILBERT_PATH}
      fill="none"
      stroke="currentColor"
      strokeWidth="2.4"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

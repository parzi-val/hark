import React, { useState, useEffect, useRef } from 'react';

interface SplashScreenProps {
  onFinished: () => void;
}

export const SplashScreen: React.FC<SplashScreenProps> = ({ onFinished }) => {
  const [fadingOut, setFadingOut] = useState(false);
  const [pathLength, setPathLength] = useState(342);
  const pathRef = useRef<SVGPathElement | null>(null);

  useEffect(() => {
    if (pathRef.current) {
      const len = pathRef.current.getTotalLength();
      if (len > 0) setPathLength(len);
    }

    // 1. Trace Hilbert curve (2.0s) + wordmark fade-in (at 1.3s) + hold (0.8s) + fadeout (0.4s)
    const timer = setTimeout(() => {
      setFadingOut(true);
      setTimeout(onFinished, 400);
    }, 3100);

    return () => clearTimeout(timer);
  }, [onFinished]);

  const handleSkip = () => {
    setFadingOut(true);
    setTimeout(onFinished, 200);
  };

  const hilbertPath =
    'M6,6 L6,11.4 L11.4,11.4 L11.4,6 L16.8,6 L22.2,6 L22.2,11.4 L16.8,11.4 L16.8,16.8 L22.2,16.8 L22.2,22.2 L16.8,22.2 L11.4,22.2 L11.4,16.8 L6,16.8 L6,22.2 L6,27.6 L11.4,27.6 L11.4,33 L6,33 L6,38.4 L6,43.8 L11.4,43.8 L11.4,38.4 L16.8,38.4 L16.8,43.8 L22.2,43.8 L22.2,38.4 L22.2,33 L16.8,33 L16.8,27.6 L22.2,27.6 L27.6,27.6 L33,27.6 L33,33 L27.6,33 L27.6,38.4 L27.6,43.8 L33,43.8 L33,38.4 L38.4,38.4 L38.4,43.8 L43.8,43.8 L43.8,38.4 L43.8,33 L38.4,33 L38.4,27.6 L43.8,27.6 L43.8,22.2 L43.8,16.8 L38.4,16.8 L38.4,22.2 L33,22.2 L27.6,22.2 L27.6,16.8 L33,16.8 L33,11.4 L27.6,11.4 L27.6,6 L33,6 L38.4,6 L38.4,11.4 L43.8,11.4 L43.8,6';

  return (
    <div
      onClick={handleSkip}
      className={`fixed inset-0 z-50 flex flex-col items-center justify-center bg-paper cursor-pointer transition-opacity duration-400 ease-out ${
        fadingOut ? 'opacity-0 pointer-events-none' : 'opacity-100'
      }`}
    >
      <div className="flex flex-col items-center">
        {/* Animated Hilbert Curve SVG */}
        <svg
          viewBox="0 0 48 48"
          className="w-28 h-28 text-ink"
        >
          <path
            ref={pathRef}
            d={hilbertPath}
            fill="none"
            stroke="currentColor"
            strokeWidth="2.4"
            strokeLinecap="round"
            strokeLinejoin="round"
            style={{
              strokeDasharray: pathLength,
              strokeDashoffset: pathLength,
              animation: 'traceHilbert 2.0s cubic-bezier(0.25, 0.05, 0.2, 1.0) forwards',
            }}
          />
        </svg>

        {/* Wordmark Fade In */}
        <h1
          className="mt-6 font-serif font-normal text-[32px] text-ink tracking-tight opacity-0"
          style={{
            animation: 'fadeInWordmark 0.7s ease-out 1.3s forwards',
          }}
        >
          Hark
        </h1>
      </div>

      <style>{`
        @keyframes traceHilbert {
          0% {
            stroke-dashoffset: ${pathLength};
          }
          100% {
            stroke-dashoffset: 0;
          }
        }
        @keyframes fadeInWordmark {
          0% {
            opacity: 0;
            transform: translateY(4px);
          }
          100% {
            opacity: 1;
            transform: translateY(0);
          }
        }
      `}</style>
    </div>
  );
};

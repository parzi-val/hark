/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        paper: 'var(--color-paper)',
        'paper-card': 'var(--color-paper-card)',
        ink: 'var(--color-ink)',
        'ink-muted': 'var(--color-ink-muted)',
        'ink-faint': 'var(--color-ink-faint)',
        'ink-hairline': 'var(--color-ink-hairline)',
        'checkbox-border': 'var(--color-checkbox-border)',
        rust: 'var(--color-rust)',
        'rust-muted': 'var(--color-rust-muted)',
      },
      fontFamily: {
        serif: ['"Libre Baskerville"', 'Georgia', 'serif'],
        mono: ['"Syne Mono"', 'monospace'],
      },
      // Type scale ported 1:1 from the Android HarkType object (px == sp).
      fontSize: {
        display: ['54px', { lineHeight: '54px' }],
        title: ['30px', { lineHeight: '34px' }],
        'note-title': ['27px', { lineHeight: '34px' }],
        item: ['17px', { lineHeight: '24px' }],
        body: ['17px', { lineHeight: '26px' }],
        'body-lg': ['18px', { lineHeight: '30px' }],
        secondary: ['14.5px', { lineHeight: '22px' }],
        label: ['11.5px', { lineHeight: '1', letterSpacing: '0.16em' }],
        meta: ['11px', { lineHeight: '1', letterSpacing: '0.16em' }],
      },
      letterSpacing: {
        mono: '0.16em',
      },
      animation: {
        'slide-in-right': 'slideInRight 0.25s cubic-bezier(0.16, 1, 0.3, 1)',
        'fade-in': 'fadeIn 0.2s ease-out',
        'pulse-subtle': 'pulseSubtle 2s infinite ease-in-out',
      },
      keyframes: {
        slideInRight: {
          '0%': { transform: 'translateX(100%)', opacity: '0' },
          '100%': { transform: 'translateX(0)', opacity: '1' },
        },
        fadeIn: {
          '0%': { opacity: '0' },
          '100%': { opacity: '1' },
        },
        pulseSubtle: {
          '0%, 100%': { opacity: '1', transform: 'scale(1)' },
          '50%': { opacity: '0.65', transform: 'scale(0.96)' },
        },
      },
    },
  },
  plugins: [],
}

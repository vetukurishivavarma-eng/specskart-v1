/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      // 2026-10 rebrand: Bricolage Grotesque headings + Plus Jakarta body (same as the POS app).
      fontFamily: {
        display: ['"Bricolage Grotesque"', 'system-ui', 'sans-serif'],
        sans: ['"Plus Jakarta Sans"', 'system-ui', 'sans-serif'],
      },
      // Token NAMES kept (ink/bone/clay/moss/slate) so every page restyles at once; the values
      // are the new identity: navy ink, cool canvas, cobalt primary, teal, coral, lime glint.
      colors: {
        ink: {
          DEFAULT: '#0A0F1F',
          soft: '#1A2340',
          muted: '#5B6475',
          faint: '#9AA1AE',
        },
        bone: {
          DEFAULT: '#F4F5F7',
          light: '#FAFBFC',
          deep: '#E8EBF0',
        },
        // "clay" is now the cobalt primary.
        clay: {
          DEFAULT: '#2342F0',
          light: '#4B65FF',
          deep: '#1A33C4',
          wash: '#E7EBFF',
        },
        moss: {
          DEFAULT: '#0E8C8C',
          light: '#2BA7A7',
          wash: '#E1F4F4',
        },
        slate: {
          DEFAULT: '#33415C',
          light: '#5B6B88',
          wash: '#E8ECF3',
        },
        coral: {
          DEFAULT: '#E8492E',
          wash: '#FFEBE5',
        },
        // The logo's lens glint -- only on dark or cobalt grounds.
        signal: '#C8F04B',
      },
      boxShadow: {
        card: '0 1px 2px rgba(10,15,31,0.04), 0 6px 20px -12px rgba(10,15,31,0.12)',
        lift: '0 2px 4px rgba(10,15,31,0.05), 0 18px 40px -16px rgba(35,66,240,0.25)',
      },
    },
  },
  plugins: [],
}

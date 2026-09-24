/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      fontFamily: {
        display: ['"Fraunces"', 'Georgia', 'serif'],
        sans: ['"Inter"', 'system-ui', 'sans-serif'],
      },
      // Four flat colours made every surface the same flat colour. Each one now carries a
      // scale, so a card can sit off the page, a badge can tint, and a heading can be a
      // shade rather than the same ink as body text.
      colors: {
        ink: {
          DEFAULT: '#14110f',
          soft: '#3a342e',
          muted: '#6f6a61',
          faint: '#9c968a',
        },
        bone: {
          DEFAULT: '#f6f3ee',
          light: '#fbf9f6',
          deep: '#ece7de',
        },
        clay: {
          DEFAULT: '#b4552d',
          light: '#d4724a',
          deep: '#8f4223',
          wash: '#f7e8e0',
        },
        moss: {
          DEFAULT: '#3f4a3c',
          light: '#5c6b58',
          wash: '#e6ebe4',
        },
        // A cool counterweight. Everything was warm, which is why the pages read as one
        // undifferentiated wash -- this is what makes the warm colours look chosen.
        slate: {
          DEFAULT: '#2f4858',
          light: '#4a6b7f',
          wash: '#e4ebf0',
        },
      },
      boxShadow: {
        card: '0 1px 2px rgba(20,17,15,0.04), 0 8px 24px -12px rgba(20,17,15,0.10)',
        lift: '0 2px 4px rgba(20,17,15,0.05), 0 16px 40px -16px rgba(20,17,15,0.18)',
      },
    },
  },
  plugins: [],
}

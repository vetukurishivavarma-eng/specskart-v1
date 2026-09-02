/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      fontFamily: {
        display: ['"Fraunces"', 'Georgia', 'serif'],
        sans: ['"Inter"', 'system-ui', 'sans-serif'],
      },
      colors: {
        ink: '#14110f',
        bone: '#f6f3ee',
        clay: '#b4552d',
        moss: '#3f4a3c',
      },
    },
  },
  plugins: [],
}

import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  // Pre-bundle mediapipe at startup instead of letting the dev server discover
  // it mid-session — that discovery stalls on "bundling dependencies…" and
  // throws the page into a ~14s reload loop (see PROGRESS.md).
  optimizeDeps: { include: ['@mediapipe/tasks-vision'] },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})

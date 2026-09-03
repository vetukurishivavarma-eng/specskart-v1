import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // 'threads' hangs (worker never responds) on this Windows box with vitest 4;
    // 'forks' is reliable.
    pool: 'forks',
    fileParallelism: false,
  },
})

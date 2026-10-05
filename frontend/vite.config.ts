import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  build: { outDir: '../build/generated-resources/frontend', emptyOutDir: true },
  server: { proxy: { '/api': 'http://127.0.0.1:3200', '/health': 'http://127.0.0.1:3200' } },
})

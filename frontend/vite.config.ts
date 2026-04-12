import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

const backendUrl = process.env.VITE_PROXY_BACKEND_URL || 'http://localhost:8080'
const ingestionUrl = process.env.VITE_PROXY_INGESTION_URL || 'http://localhost:8081'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 3000,
    host: true,
    proxy: {
      '/api': {
        target: backendUrl,
        changeOrigin: true,
      },
      '/health': {
        target: backendUrl,
        changeOrigin: true,
      },
      '/auth': {
        target: backendUrl,
        changeOrigin: true,
      },
      '/ingestion-api': {
        target: ingestionUrl,
        changeOrigin: true,
        rewrite: (path: string) => path.replace(/^\/ingestion-api/, ''),
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/setupTests.ts',
  },
})

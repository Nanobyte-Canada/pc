import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

const backendUrl = process.env.VITE_PROXY_BACKEND_URL || 'http://localhost:8080'
const ingestionUrl = process.env.VITE_PROXY_INGESTION_URL || 'http://localhost:8081'
const marketDataUrl = process.env.VITE_PROXY_MARKET_DATA_URL || 'http://localhost:8082'
const strategyUrl = process.env.VITE_PROXY_STRATEGY_URL || 'http://localhost:8083'

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
      '/market-data-api': {
        target: marketDataUrl,
        changeOrigin: true,
        rewrite: (path: string) => path.replace(/^\/market-data-api/, ''),
      },
      '/ws/quotes': {
        target: marketDataUrl,
        ws: true,
        changeOrigin: true,
      },
      '/strategy-api': {
        target: strategyUrl,
        changeOrigin: true,
        rewrite: (path: string) => path.replace(/^\/strategy-api/, ''),
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
    exclude: ['e2e/**', 'node_modules/**', 'dist/**', '.idea/**', '.git/**', '.cache/**'],
  },
})

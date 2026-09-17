import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/accounts': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/transactions': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      '/dev': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      '/ledger': {
        target: 'http://localhost:8083',
        changeOrigin: true,
      },
    },
  },
})

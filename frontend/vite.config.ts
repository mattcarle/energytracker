import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Binds the dev server to all network interfaces, not just localhost, so it's reachable at
    // the machine's LAN IP (e.g. from a phone on the same Wi-Fi) - Vite defaults to localhost-only.
    host: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})

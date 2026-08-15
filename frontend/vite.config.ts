import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // Lets `npm run dev`/`preview` point the API proxy at a backend other than the default dev
  // instance (e.g. VITE_API_PROXY_TARGET=https://localhost:8443 to test against a locally
  // running `prod`-profile backend) without editing this file. `vite preview` reuses this same
  // `server.proxy` config unless a separate `preview.proxy` is set.
  const env = loadEnv(mode, process.cwd(), '')
  const apiProxyTarget = env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080'

  return {
    plugins: [react()],
    server: {
      // Binds the dev server to all network interfaces, not just localhost, so it's reachable at
      // the machine's LAN IP (e.g. from a phone on the same Wi-Fi) - Vite defaults to localhost-only.
      host: true,
      proxy: {
        '/api': {
          target: apiProxyTarget,
          changeOrigin: true,
          // Only takes effect for https targets - lets the proxy reach a backend using the
          // self-signed cert from the README's "Running in production" section.
          secure: false,
        },
      },
    },
  }
})

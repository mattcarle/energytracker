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

  // The app is served at https://<domain>/energytracker/ by default (see frontend/Caddyfile),
  // not the site root, so every built asset reference needs this prefix or the browser requests
  // them from the wrong path once index.html is served from a subpath. Overridable so a second
  // instance of this same app can be built to live at a different prefix (e.g. /energytracker2/)
  // behind the shared reverse proxy - see frontend/Dockerfile's APP_BASE_PATH build arg and
  // frontend/Caddyfile's matching $APP_PATH, which both need to agree with this.
  const basePath = env.APP_BASE_PATH || '/energytracker/'
  const apiPathPrefix = basePath.replace(/\/$/, '')

  return {
    base: basePath,
    plugins: [react()],
    server: {
      // Binds the dev server to all network interfaces, not just localhost, so it's reachable at
      // the machine's LAN IP (e.g. from a phone on the same Wi-Fi) - Vite defaults to localhost-only.
      host: true,
      proxy: {
        // The frontend calls <basePath>/api/... (see src/api/client.ts) so its requests carry the
        // same path prefix in dev as they will once deployed behind the shared reverse proxy (see
        // frontend/Caddyfile) - the backend itself is still mapped at bare /api/..., so the prefix
        // is stripped here before forwarding.
        [`${apiPathPrefix}/api`]: {
          target: apiProxyTarget,
          changeOrigin: true,
          rewrite: (path) => path.replace(new RegExp(`^${apiPathPrefix}`), ''),
          // Only takes effect for https targets - lets the proxy reach a backend using the
          // self-signed cert from the README's "Running in production" section.
          secure: false,
        },
      },
    },
  }
})

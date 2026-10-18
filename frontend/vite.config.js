import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Build straight into Spring Boot's static resources so `mvn package` bundles
// the SPA. During `npm run dev`, proxy the API to the running backend.
//
// `base` is relative for the production build so assets resolve under any
// servlet context-path (the backend injects a matching <base href>); the dev
// server stays at root.
export default defineConfig(({ command }) => ({
  base: command === 'build' ? './' : '/',
  plugins: [react()],
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
}));

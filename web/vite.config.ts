import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // The Spring Boot back end runs on 8080; the browser only talks to Vite
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  optimizeDeps: {
    // MapLibre 6 loads its web worker from a file next to its own module. Pre-bundling moves the module and breaks that path.
    exclude: ['maplibre-gl'],
  },
})

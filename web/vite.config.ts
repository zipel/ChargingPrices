import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Fail instead of quietly moving to another port, so the address in the README and start script stays right
    strictPort: true,
    // The Spring Boot back end runs on 8080; the browser only talks to Vite
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})

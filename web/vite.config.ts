import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  // Relative asset paths, so the build works from whatever folder serves it
  // (a GitHub Pages project path, a preview), not only a domain's root.
  base: './',
  plugins: [react(), tailwindcss()],
})

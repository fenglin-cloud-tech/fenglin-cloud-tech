import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const gateway = process.env.FENGLIN_GATEWAY ?? 'http://localhost:18080'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5174,
    proxy: {
      '/api': { target: gateway, changeOrigin: true },
      '/actuator': { target: gateway, changeOrigin: true },
    },
  },
  build: {
    outDir: 'dist',
  },
})

import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      // Алиас `@` нужен shadcn/ui: сгенерированные компоненты импортируют
      // друг друга и `@/lib/utils` только через него
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '0.0.0.0',  // Слушаем все интерфейсы
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',  // Прокси для localhost
        changeOrigin: true,
      }
    }
  }
})

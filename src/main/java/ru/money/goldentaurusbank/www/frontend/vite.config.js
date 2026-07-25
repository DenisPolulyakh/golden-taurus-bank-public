import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
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
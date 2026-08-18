import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import path from 'node:path'

/**
 * Tauri 专用 Vite 配置
 *
 * 共享根目录 src/ 作为源代码，输出到 tauri-app/dist/
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      // @/ → root/src/
      '@': fileURLToPath(new URL('../src', import.meta.url)),
      // Allow ../src/ imports from tauri-app/src/main.ts
      '../src': path.resolve(__dirname, '../src'),
    }
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    ssr: false,
    rollupOptions: {
      input: fileURLToPath(new URL('../index.html', import.meta.url)),
    }
  },
  define: {
    'import.meta.env.VITE_API_BASE_URL': JSON.stringify('http://localhost:8080/api'),
    'import.meta.env.VITE_WS_URL': JSON.stringify('http://localhost:8080/ws'),
  }
})

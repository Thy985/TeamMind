import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

/**
 * Tauri 专用 Vite 配置
 *
 * 构建输出到 tauri-app/dist/，供 Tauri 加载。
 * 共享根目录 src/ 下的所有代码。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('../src', import.meta.url))
    }
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    ssr: false,
  },
  define: {
    'import.meta.env.VITE_API_BASE_URL': JSON.stringify('http://localhost:8080/api'),
    'import.meta.env.VITE_WS_URL': JSON.stringify('http://localhost:8080/ws'),
  }
})

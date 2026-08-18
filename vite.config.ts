/**
 * TeamMind — Vite 双模式配置
 *
 * 此文件被 teammind (web) 和 teammind-tauri (desktop) 共同使用。
 *
 * 通过 TORMODE 环境变量区分：
 * - 默认 / TORMODE=web → 标准 web 开发服务器
 * - TORMODE=tauri     → Tauri 构建模式（无 HMR，输出到指定目录）
 */
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const isTauri = env.TORMODE === 'tauri'

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    ...(isTauri
      ? {
          // Tauri 构建模式
          build: {
            outDir: 'tauri-app/dist',
            emptyOutDir: true,
            ssr: false,
          },
          define: {
            'import.meta.env.VITE_API_BASE_URL': JSON.stringify('http://localhost:8080/api'),
            'import.meta.env.VITE_WS_URL': JSON.stringify('http://localhost:8080/ws'),
          },
        }
      : {
          // Web 开发模式
          server: {
            port: 3000,
            open: true,
            proxy: {
              '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
              },
              '/ws': {
                target: 'ws://localhost:8080',
                ws: true,
                changeOrigin: true,
              },
            },
          },
          define: {
            'import.meta.env.VITE_API_BASE_URL': JSON.stringify('/api'),
            'import.meta.env.VITE_WS_URL': JSON.stringify('/ws'),
          },
        }
    )
  }
})

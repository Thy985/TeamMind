import { createApp } from 'vue'
import { createPinia } from 'pinia'
import router from './router'
import App from './App.vue'
import { setupGlobalErrorHandler } from './plugins/errorHandler'
import { initHostAdapter } from './api/hostAdapter'

// Import styles
import './styles/index.css'

// Initialize Host Adapter (auto-detects web vs Tauri mode)
const adapter = initHostAdapter()
console.log(`[TeamMind] HostAdapter: ${adapter.mode} mode`)

// Create app
const app = createApp(App)

// Use plugins
app.use(createPinia())
app.use(router)

// Setup global error handler
setupGlobalErrorHandler(app)

// Mount app
app.mount('#app')

// 初始化 WebSocket 连接
import { wsManager } from './api/websocket'
wsManager.connect().catch(error => {
  console.error('Failed to connect WebSocket:', error)
})

// 页面卸载时断开 WebSocket 连接
window.addEventListener('beforeunload', () => {
  wsManager.disconnect()
})

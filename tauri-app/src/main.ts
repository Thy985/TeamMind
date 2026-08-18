/**
 * TeamMind Tauri Entry Point
 *
 * 与 src/main.ts 的区别：
 * - 自动初始化 HostAdapter（Tauri 模式）
 * - 不连接 WebSocket（M4 阶段替换为 Tauri event stream）
 * - 注册 Tauri 平台特定功能（tray、native menu 等）
 */
import { createApp } from "vue"
import { createPinia } from "pinia"
import router from "../src/router"
import App from "../src/App.vue"
import { setupGlobalErrorHandler } from "../src/plugins/errorHandler"
import { initHostAdapter } from "../src/api/hostAdapter"

// 导入样式
import "../src/styles/index.css"

// 初始化 Host Adapter
const adapter = initHostAdapter()
console.log(`[TeamMind] HostAdapter initialized: ${adapter.mode} mode`)

// 创建 Vue App
const app = createApp(App)
app.use(createPinia())
app.use(router)
setupGlobalErrorHandler(app)
app.mount("#app")

// WebSocket 连接（M1: 仍使用 HTTP proxy，M4: 切换到 Tauri event stream）
import { wsManager } from "../src/api/websocket"
wsManager.connect().catch(error => {
  console.warn("[TeamMind] WebSocket in Tauri mode:", error)
})

window.addEventListener("beforeunload", () => {
  wsManager.disconnect()
})

console.log("[TeamMind] Desktop runtime ready. Backend on localhost:8080")
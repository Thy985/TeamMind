/**
 * TeamMind Tauri Entry Point
 *
 * M1: Tauri 仅作为桌面壳，UI/逻辑完全复用根目录 src/
 * HostAdapter 自动检测 Tauri 环境并切换到 tauri::invoke 模式
 */
import { createApp } from "vue"
import { createPinia } from "pinia"
import router from "../src/router"
import App from "../src/App.vue"
import { setupGlobalErrorHandler } from "../src/plugins/errorHandler"
import { initHostAdapter } from "../src/api/hostAdapter"
import "../src/styles/index.css"

// Initialize Host Adapter (auto-detects Tauri env)
const adapter = initHostAdapter()
console.log("[TeamMind Tauri] HostAdapter:", adapter.mode)

const app = createApp(App)
app.use(createPinia())
app.use(router)
setupGlobalErrorHandler(app)
app.mount("#app")

// WebSocket — only in web mode; M4: replace with Tauri event stream
if (adapter.mode === "web") {
  import("../src/api/websocket").then(({ wsManager }) => {
    wsManager.connect().catch((e) => console.warn("[TeamMind] WS error:", e))
    window.addEventListener("beforeunload", () => wsManager.disconnect())
  })
}

console.log("[TeamMind Tauri] Ready. Spring Boot on localhost:8080")

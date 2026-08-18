export { api, missionApi, agentApi, templateApi, missionControlApi, taskDetailApi } from './axios'
export { wsManager, WebSocketManager } from './websocket'
export type { WebSocketOptions } from './websocket'

// ─── Host Adapter (M1: dual-mode web/tauri communication) ────────
export {
  initHostAdapter,
  forceHostAdapter,
  getHostAdapter,
} from './hostAdapter'
export type { HostAdapter, ApiResponse } from './hostAdapter'

// ─── Adapter-aware API (M1: Tauri mode routing) ───────────────────
// These APIs route through HostAdapter in Tauri mode,
// and through axios in web mode (via the existing api objects above).
export { projectApi, taskApi, mcApi, agentApiV2, knowledgeApiV2, healthApi } from './adapterApi'

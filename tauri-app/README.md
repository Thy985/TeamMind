# TeamMind Tauri — Phase M1

> **目标：在不修改任何 Java/Rust 代码的前提下，让 Vue UI 以桌面应用形式运行。**

## 架构

```
┌─────────────────────────────────────────────────────┐
│                   TeamMind App                      │
│                                                     │
│  ┌─────────────────┐        ┌─────────────────────┐ │
│  │   Vue 3 UI      │  invoke │  Rust Tauri Commands │ │
│  │  (不变)          │◄──────▶│  (proxy to Spring)   │ │
│  │                 │        │                      │ │
│  │  HostAdapter    │        │  /api/* → Spring     │ │
│  │  (自动检测模式)  │        │  /ws/* → Spring WS   │ │
│  └─────────────────┘        └──────────┬──────────┘ │
│                                        │            │
│                              ┌─────────▼─────────┐  │
│                              │  Spring Boot      │  │
│                              │  (保持不变)        │  │
│                              └───────────────────┘  │
└─────────────────────────────────────────────────────┘
```

## 工作原理

### 前端
- `src/api/hostAdapter.ts` — 新增抽象层
  - `isTauriEnv()` — 检测是否在 Tauri 环境
  - `WebHostAdapter` — 现有 HTTP + WebSocket 逻辑
  - `TauriHostAdapter` — 通过 `tauri::invoke` 调用 Rust commands
- `tauri-app/src/main.ts` — Tauri 专用入口
  - 自动初始化 Tauri adapter
  - **仅在 web 模式下连接 WebSocket**（M4 阶段替换为 Tauri event stream）

### 后端（Rust proxy）
- 每个 Tauri command 都是 Spring Boot API 的代理
- `runtime_invoke` — 转发 POST/PUT 请求
- `runtime_stream` — 转发 GET 请求
- 所有命令返回统一的 `ApiResponse<T>` 格式

## M1 Exit Gate（来自 Claude Code review）

- [x] `tauri-app/` 目录结构创建
- [x] Cargo.toml + tauri.conf.json 配置
- [x] commands.rs 覆盖所有核心 API
- [x] hostAdapter.ts 抽象层实现
- [x] Tauri 入口 main.ts
- [ ] `pnpm tauri:dev` 能启动（需要 Rust + Tauri CLI）
- [ ] 桌面窗口能显示 Vue UI
- [ ] 点击"创建任务"等操作能实际调用 Spring Boot

## 已知限制（M1 Proxy Mode）

- 所有 API 调用通过 Rust proxy 转发到 Spring Boot
- WebSocket 连接在 Tauri 模式下已禁用（M4 阶段添加 Tauri event stream）
- 离线模式不支持
- 进程管理仍由 Java 侧处理（M2 迁移到 Rust）

## 下一步（M2）

当 M1 exit gate 全部通过后，开始第一阶段能力迁移：
1. Java `ProcessSupervisor` 接口已由 Codex 实现
2. 实现 Rust `TokioProcessSupervisor`
3. Feature Flag: `runtime.process.provider = rust`
4. Contract Equivalence Test 验证等价性
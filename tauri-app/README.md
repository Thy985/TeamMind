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

## 文件结构

```
tauri-app/
├── src-tauri/
│   ├── Cargo.toml          # Rust 依赖
│   ├── tauri.conf.json     # Tauri 配置
│   ├── build.rs            # 构建脚本
│   └── src/
│       ├── main.rs         # 入口（command 注册）
│       └── commands.rs     # 所有 Tauri commands
├── src/
│   └── main.ts             # Vue 入口（Tauri 模式）
├── vite.config.ts          # Vite 配置（输出到 dist/）
└── package.json
```

## 工作原理

### 前端
- `src/api/hostAdapter.ts` — 新增抽象层
  - `isTauriEnv()` — 检测是否在 Tauri 环境
  - `WebHostAdapter` — 现有 HTTP + WebSocket 逻辑
  - `TauriHostAdapter` — 通过 `tauri::invoke` 调用 Rust commands
- `tauri-app/src/main.ts` — Tauri 专用入口
  - 自动初始化 Tauri adapter
  - 跳过 WebSocket 连接（M4 阶段替换）

### 后端（Rust proxy）
- 每个 Tauri command 都是 Spring Boot API 的代理
- `runtime_invoke` — 转发 POST/PUT 请求
- `runtime_stream` — 转发 GET 请求
- 所有命令返回统一的 `ApiResponse<T>` 格式

## 开发流程

### 启动 Tauri 开发模式
```bash
# 1. 确保 Spring Boot 在运行
cd backend && mvn spring-boot:run

# 2. 启动 Tauri（会自动 build Vue + 启动桌面窗口）
cd tauri-app && pnpm tauri dev
```

### 构建桌面应用
```bash
cd tauri-app && pnpm tauri build
# 产出: target/release/bundle/
```

## M1 验收标准

- [x] `tauri-app/` 目录结构创建
- [x] Cargo.toml + tauri.conf.json 配置
- [x] commands.rs 覆盖所有核心 API
- [x] hostAdapter.ts 抽象层实现
- [x] Tauri 入口 main.ts
- [ ] `pnpm tauri dev` 能启动（需要安装 Rust + Tauri CLI）
- [ ] 桌面窗口能显示 Vue UI
- [ ] 点击"创建任务"等操作能实际调用 Spring Boot

## 下一步（M2）

当 M1 验证通过后，开始第一阶段能力迁移：
1. 提取 `ProcessSupervisor` 接口到 Java
2. 实现 Rust `ProcessSupervisor`
3. Feature Flag: `runtime.process.provider = rust`

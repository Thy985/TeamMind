# 🧠 TeamMind

一个支持智能体自主进化的 AI Agent 协作平台，支持多 Agent 实时协作可视化。

## 📋 技术栈

### 前端
- **框架**: Vue 3.5+ (Composition API + `<script setup>`)
- **语言**: TypeScript (严格模式)
- **构建工具**: Vite 5+
- **状态管理**: Pinia 3+
- **路由**: Vue Router 4
- **UI 组件库**: Naive UI 2.44+
- **协作画布**: Vue Flow 1.48+

### 后端
- **框架**: Spring Boot 3.2+
- **语言**: Java 17+
- **数据库**: SQLite (零配置)
- **配置存储**: Markdown 文件
- **实时通信**: WebSocket (STOMP)

### LLM 支持
- ✅ **百度千帆** (ERNIE-4.0, ERNIE-3.5 等)
- ✅ **OpenAI** (GPT-4, GPT-3.5)
- ✅ **Anthropic** (Claude 3)
- 🔧 **Azure OpenAI**
- 🔧 **本地模型** (Ollama)

## 🚀 快速开始

### 环境要求

- Node.js 18+
- JDK 17+
- Maven 3.8+

### 一键启动（推荐）

Windows 用户：

```bash
# 双击运行或在命令行执行
start-all.bat
```

这将同时启动后端 (localhost:8080) 和前端 (localhost:3000)。

### 手动启动

#### 1. 启动后端

```bash
cd backend

# Windows
start.bat

# 或手动设置环境变量
export QIANFAN_API_KEY=bce-v3/ALTAKSP-W8fnSI7P0cqEcBxE6SYuM/94fd0c1e5615d09b244576df2d68dfd0739ed5f2
mvn spring-boot:run
```

#### 2. 启动前端

```bash
# 安装依赖（首次）
npm install

# 启动开发服务器
npm run dev
```

访问 http://localhost:3000

## 🔧 配置

### LLM 配置

后端支持多种 LLM 提供商，在 `backend/src/main/resources/application.yml` 中配置：

```yaml
teammind:
  llm:
    default-provider: qianfan  # 可选: qianfan, openai, anthropic
    
    # 百度千帆（推荐）
    qianfan:
      api-key: ${QIANFAN_API_KEY}
      base-url: https://qianfan.baidubce.com/v2/coding
      default-model: ERNIE-4.0-8K
    
    # OpenAI（备用）
    openai:
      api-key: ${OPENAI_API_KEY}
    
    # Anthropic（备用）
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
```

### 前端环境变量

```bash
# .env.development
VITE_API_BASE_URL=http://localhost:8080/api
VITE_WS_URL=ws://localhost:8080/ws
```

## 📁 项目结构

```
TeamMind/
├── src/                      # 前端源码
│   ├── api/                  # API 通信
│   ├── components/           # Vue 组件
│   ├── pages/                # 页面组件
│   ├── stores/               # Pinia 状态管理
│   └── types/                # TypeScript 类型
│
├── backend/                  # 后端源码
│   ├── src/main/java/com/teammind/
│   │   ├── controller/       # REST API
│   │   ├── service/          # 业务逻辑
│   │   ├── entity/           # 数据库实体
│   │   ├── llm/              # LLM 集成
│   │   ├── evolution/        # 进化引擎
│   │   ├── executor/         # 执行引擎
│   │   └── websocket/        # WebSocket
│   └── src/main/resources/
│       ├── application.yml   # 配置文件
│       └── agents/           # Agent Markdown 配置
│
├── start-all.bat             # 一键启动脚本
├── start-frontend.bat        # 启动前端
└── backend/start.bat         # 启动后端
```

## 🎯 核心功能

### Agent 进化能力

| 能力 | 说明 |
|------|------|
| **Prompt 自我优化** | LLM 分析反馈，自动优化 Agent Prompt |
| **工具自动生成** | 根据需求描述生成新的工具代码 |
| **协作拓扑进化** | 动态优化多 Agent 协作结构 |
| **版本管理** | 保存进化历史，支持回滚 |

### API 端点

| 模块 | 主要接口 |
|------|----------|
| Mission | `/api/missions` - 任务 CRUD、启动、暂停、克隆 |
| Agent | `/api/agents` - Agent 管理、进化、执行 |
| Template | `/api/templates` - 团队模板管理 |
| LLM | `/api/llm` - LLM 状态、测试、聊天 |
| Usage | `/api/usage` - 使用统计 |

### WebSocket 事件

连接 `ws://localhost:8080/ws`，订阅：
- `/topic/events` - 全局事件
- `/topic/missions/{id}` - 任务事件

事件类型：`mission_started`, `agent_spawned`, `node_update`, `log`, `evolution_triggered` 等

## 📄 页面说明

| 页面 | 路径 | 功能 |
|------|------|------|
| Dashboard | `/` | 任务启动、快速模板、统计 |
| Mission Detail | `/missions/:id` | 协作画布、实时日志、控制 |
| History | `/history` | 任务历史、克隆、删除 |
| Market | `/market` | Agent 浏览、安装、进化 |
| Templates | `/templates` | 团队模板管理 |
| Settings | `/settings` | LLM 配置、主题设置 |

## 🧪 测试 API

```bash
# 测试 LLM 连接
curl -X POST http://localhost:8080/api/llm/test

# 获取 LLM 状态
curl http://localhost:8080/api/llm/status

# 获取 Agent 列表
curl http://localhost:8080/api/agents

# 触发 Agent 进化
curl -X POST http://localhost:8080/api/agents/agent-1/evolve \
  -H "Content-Type: application/json" \
  -d '{"type": "PROMPT_OPTIMIZATION", "reason": "Improve accuracy"}'
```

## 📜 License

MIT

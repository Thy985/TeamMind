# TeamMind Backend

AI Agent 协作平台后端服务，支持智能体自主进化。

## 技术栈

- **框架**: Spring Boot 3.2+
- **Java**: 17+
- **数据库**: SQLite (零配置)
- **配置存储**: Markdown 文件
- **实时通信**: WebSocket (STOMP)
- **LLM 集成**: OpenAI / Azure OpenAI / 本地模型

## 特性

### 核心功能
- ✅ Mission 任务管理 (CRUD + 启动/暂停/克隆)
- ✅ Agent 智能体管理 (安装/卸载/启用)
- ✅ Template 团队模板管理
- ✅ WebSocket 实时事件推送
- ✅ **LLM 集成** - 支持 OpenAI、Azure OpenAI、本地模型

### 智能体进化能力
- ✅ **Prompt 自我优化** - 使用 LLM 自动优化 Agent Prompt
- ✅ **工具自动生成** - LLM 根据需求生成工具代码
- ✅ **协作拓扑进化** - LLM 分析优化多 Agent 协作结构
- ✅ **进化历史版本管理** - 支持回滚到任意版本

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+

### 本地运行

```bash
# 编译项目
mvn clean package -DskipTests

# 运行
java -jar target/teammind-backend-0.1.0.jar
```

或使用 Maven：

```bash
mvn spring-boot:run
```

### 配置

编辑 `src/main/resources/application.yml`：

```yaml
# 数据存储路径
teammind:
  data-path: ~/.teammind
  agents-path: ${teammind.data-path}/agents
  templates-path: ${teammind.data-path}/templates
  evolution-path: ${teammind.data-path}/evolution

# LLM 配置
  llm:
    default-provider: openai
    default-model: gpt-4-turbo-preview
    api-key: ${OPENAI_API_KEY}
    base-url: ${OPENAI_BASE_URL:https://api.openai.com/v1}
```

### LLM 配置

支持多种 LLM 提供商：

#### OpenAI

```bash
# 设置环境变量
export OPENAI_API_KEY=sk-your-api-key
export OPENAI_BASE_URL=https://api.openai.com/v1  # 可选，用于代理
```

#### Azure OpenAI

```yaml
teammind:
  llm:
    azure:
      enabled: true
      api-key: ${AZURE_OPENAI_KEY}
      endpoint: ${AZURE_OPENAI_ENDPOINT}
      deployment-name: ${AZURE_OPENAI_DEPLOYMENT}
```

#### 本地模型 (Ollama)

```yaml
teammind:
  llm:
    local:
      enabled: true
      base-url: http://localhost:11434
      model: llama2
```

### LLM API

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/llm/status` | 获取 LLM 状态 |
| GET | `/api/llm/providers` | 获取可用提供商 |
| POST | `/api/llm/test` | 测试 LLM 连接 |
| POST | `/api/llm/chat` | 发送聊天请求 |
| POST | `/api/llm/chat/{provider}` | 使用指定提供商聊天 |

## API 文档

### Mission API

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/missions` | 创建任务 |
| GET | `/api/missions` | 获取任务列表 |
| GET | `/api/missions/{id}` | 获取任务详情 |
| PUT | `/api/missions/{id}` | 更新任务 |
| DELETE | `/api/missions/{id}` | 删除任务 |
| POST | `/api/missions/{id}/start` | 启动任务 |
| POST | `/api/missions/{id}/pause` | 暂停任务 |
| POST | `/api/missions/{id}/clone` | 克隆任务 |
| POST | `/api/missions/{id}/nodes/{nodeId}/retry` | 重试节点 |
| POST | `/api/missions/{id}/nodes/{nodeId}/skip` | 跳过节点 |

### Agent API

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/agents` | 获取所有 Agent |
| GET | `/api/agents/installed` | 获取已安装 Agent |
| GET | `/api/agents/{id}` | 获取 Agent 详情 |
| POST | `/api/agents` | 创建自定义 Agent |
| POST | `/api/agents/{id}/install` | 安装 Agent |
| DELETE | `/api/agents/{id}` | 卸载 Agent |
| PUT | `/api/agents/{id}/enabled` | 切换启用状态 |
| POST | `/api/agents/{id}/evolve` | 触发进化 |
| GET | `/api/agents/{id}/evolution/history` | 获取进化历史 |
| POST | `/api/agents/{agentId}/evolution/{recordId}/rollback` | 回滚进化 |
| POST | `/api/agents/{id}/execute` | 执行 Agent 任务 |
| GET | `/api/agents/{id}/stream` | 流式执行 Agent (SSE) |
| GET | `/api/agents/{id}/usage` | 获取 Agent 使用统计 |

### 执行 API

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/missions/{id}/run` | 启动任务执行 |
| POST | `/api/missions/{id}/pause-execution` | 暂停任务执行 |
| POST | `/api/missions/{id}/resume-execution` | 恢复任务执行 |
| POST | `/api/missions/{id}/cancel` | 取消任务执行 |
| GET | `/api/missions/{id}/runtime` | 获取任务运行状态 |
| GET | `/api/usage/stats` | 获取 LLM 使用统计 |
| GET | `/api/usage/today` | 获取今日使用统计 |

### Template API

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/templates` | 获取所有模板 |
| GET | `/api/templates/public` | 获取公开模板 |
| GET | `/api/templates/mine` | 获取我的模板 |
| GET | `/api/templates/{id}` | 获取模板详情 |
| POST | `/api/templates` | 创建模板 |
| PUT | `/api/templates/{id}` | 更新模板 |
| DELETE | `/api/templates/{id}` | 删除模板 |
| POST | `/api/templates/{id}/use` | 使用模板 |

### WebSocket 事件

连接到 `ws://localhost:8080/ws`

订阅主题：
- `/topic/events` - 全局事件
- `/topic/missions/{id}` - 特定任务事件

事件类型：
- `mission_started` - 任务启动
- `mission_completed` - 任务完成
- `agent_spawned` - Agent 创建
- `agent_status_update` - Agent 状态更新
- `node_update` - 节点更新
- `log` - 日志消息
- `evolution_triggered` - 进化触发
- `evolution_completed` - 进化完成

## 智能体进化

### Prompt 优化

```bash
POST /api/agents/{id}/evolve
{
  "type": "PROMPT_OPTIMIZATION",
  "reason": "Based on performance feedback",
  "automatic": false
}
```

### 工具生成

```bash
POST /api/agents/{id}/evolve
{
  "type": "TOOL_GENERATION",
  "context": {
    "required_capability": "data_transformation",
    "description": "Transform JSON to CSV"
  }
}
```

### 进化回滚

```bash
POST /api/agents/{agentId}/evolution/{recordId}/rollback
```

## 项目结构

```
src/main/java/com/teammind/
├── TeamMindApplication.java    # 主应用
├── config/                      # 配置类
│   ├── CorsConfig.java
│   └── WebSocketConfig.java
├── controller/                  # REST 控制器
│   ├── AgentController.java
│   ├── MissionController.java
│   └── TemplateController.java
├── dto/                         # 数据传输对象
├── entity/                      # 数据库实体
│   ├── Agent.java
│   ├── EvolutionRecord.java
│   ├── Mission.java
│   └── TeamTemplate.java
├── evolution/                   # 进化引擎
│   └── EvolutionEngine.java
├── repository/                  # 数据访问层
├── service/                     # 业务逻辑层
└── websocket/                   # WebSocket 支持
    ├── WSEvent.java
    ├── WSEventPublisher.java
    └── WebSocketController.java
```

## Docker 部署

```bash
# 构建镜像
docker build -t teammind-backend .

# 运行容器
docker run -p 8080:8080 \
  -e OPENAI_API_KEY=your-key \
  -v teammind-data:/data/teammind \
  teammind-backend

# 或使用 docker-compose
docker-compose up -d
```

## License

MIT

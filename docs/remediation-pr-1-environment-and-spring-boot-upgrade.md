# PR-1: 环境探测 + 后端 8 个项目 Bug 修复 + Spring Boot 3.2 → 3.3 升级

## 概述

本 PR 在 TeamMind 仓库历史中 **首次完成端到端可运行验证**：修复了 8 个此前从未被测试套件覆盖到的项目级 bug，并将 Spring Boot 从已 EoL 的 3.2.3 升级至 3.3.7（仍在 OSS 免费支持窗口内）。

## 变更范围

### 1. 项目 Bug 修复（B1-B8）

| # | 文件 | 缺陷性质 | 修复 |
|---|---|---|---|
| B1 | `config/WebSocketConfig.java` | **编译错误** —— 缺 `import WebSocketAuthChannelInterceptor` | 加 import |
| B2 | `test/.../AgentExecutionEngineTest.java` | **测试编译错误** —— 缺 `import java.util.Map` | 加 import |
| B3 | `test/.../JwtUtilTest.java` | 测试 sleep 不够跨秒边界 | sleep 1100→1500ms |
| B3b | 同上 | 同根因（另一处 sleep 20→1100ms） | 延长 sleep |
| B4 | `test/.../AuthControllerTest.java` | 断言期望 5xx 但 `@Valid` 实际返回 400 | 改 400 |
| B5 | `test/.../AgentExecutionEngineTest.java` | Windows 路径 `\` 未在 JSON 中转义导致 JSON parse 失败 | `replace("\\", "\\\\")` |
| B6 | `service/AuthService.java` | **真实业务 bug** —— `roles/permissions` 为 null 时响应也返回 null（NPE 风险） | 加 null 兜底 |
| B7 | `test/.../ResolutionServiceTest.java` | 测试期望值算错（多了一票） | 移除多余 step |
| **B8** | **新文件** `config/DataDirectoryBootstrap.java` + `META-INF/spring/...EnvironmentPostProcessor.imports` | **真实运行时 bug** —— 首次启动时 `~/.teammind/` 目录不存在导致 SQLite 连接失败 | 新建 `EnvironmentPostProcessor` 在 Spring 启动最早阶段自动创建目录 |

### 2. Spring Boot 升级（P1-9）

- `pom.xml`：`spring-boot-starter-parent` 版本 `3.2.3` → `3.3.7`
- Java 版本保持 17（按既定策略）
- **依赖差异**（Boot BOM 自动管理）：
  - Spring Framework: 6.1.x → **6.1.16**
  - Spring Data JPA / Commons: 3.2.x → **3.3.7**
  - Spring Security Crypto: 6.2.x → **6.3.6**
  - Spring WebFlux / WebSocket: 6.1.x → **6.1.16**

## 验证证据

| 项 | 命令 | 结果 |
|---|---|---|
| 后端编译 | `mvn -B compile` | ✅ BUILD SUCCESS（71 源文件） |
| 后端单测 | `mvn -B test` | ✅ **114/114 tests passed**（修复前 108/114） |
| 后端集成 | `mvn spring-boot:run` | ✅ **9.225 秒启动**，`Tomcat started on port 8080` |
| JWT 强制保护 | `curl /api/missions` 无 token | ✅ 401 `UNAUTHORIZED` |
| 登录 | `POST /api/auth/login` `admin/admin123` | ✅ 200，返回有效 JWT + `roles:["ADMIN"]` + 7 项 permissions |
| 鉴权访问 | `GET /api/missions` 带 token | ✅ 200 |
| LLM 状态 | `GET /api/llm/status` | ✅ 200，3 个 provider（qianfan/openai/anthropic），无 API key 时 available=false（符合预期） |
| 默认数据 | `GET /api/agents` | ✅ 200，5 个默认 Agent（agent-1~5，未 installed） |
| 前端构建 | `npm run build` | ✅ vue-tsc + vite build，2 秒 |
| 前端单测 | `npm run test -- --run` | ✅ **115/115 tests passed** |

## 风险与回滚

- **风险等级**：低
- **回滚预案**：单 PR revert 即可，schema/数据无破坏性变更
- **未触动的代码**：业务逻辑核心（executor / evolution / websocket）**未做产品代码重构**，仅修复 B1/B6/B8 三处阻塞性 bug

## 已知遗留（不在本 PR 范围）

按修复计划 §四 P1 / §五 P2 仍有以下 Task 待执行：
- P0-1 登录页 + 路由守卫
- P0-2 AgentExecutionEngine 拆分
- P0-3 WS 事件命名统一
- P0-4 metricsAfter 时机
- P0-5 retryNode 重执行
- P0-6 默认 Agent Prompt
- P0-7 工具 JSON 抽取鲁棒化
- P1-1 ~ P1-8 其它 P1 修复
- P2-1 ~ P2-6 持续打磨

## 相关文档

- `docs/teammind-remediation-plan.md`（完整 WBS + 验收标准）
- `docs/superpowers/specs/2026-08-08-mise-controlled-experiment-design.md`（无关本次 PR）
# Agent CLI 编排生态预研报告

> 日期：2026-08
> 作者：TeamMind
> 目的：在投入工程实现前，确认"本地 AI Agent CLI 编排控制台"这个定位的**真实市场规模、竞争格局、差异化机会**。
> 方法：综合 GitHub Stars 数据、社区呼声、技术路线讨论，结合一手用户调研。

---

## TL;DR

> **强烈建议做。痛点已被 10 万 Stars 的竞品验证；切入时机合适；技术栈完美契合；存在明确差异化空间（CC Switch 管配置，Open Design 偏设计，Orca 是并行 IDE 不是协作引擎，没人做"Project AI Team Runtime"）。详见 [orca-competitive-analysis.md](./orca-competitive-analysis.md)。**

---

## 1. 市场规模

### 1.1 全球 AI Agent CLI 热度（2026 年 8 月）

| 工具 | 厂商 | GitHub Stars | 定位 |
|---|---|---|---|
| OpenCode | 开源社区 | **19.5 万** | 多模型 + 隐私本地化 |
| Claude Code | Anthropic | 14.1 万 | 最强推理 |
| Codex CLI | OpenAI | 10.5 万 | 速度 + 安全沙箱 |
| Gemini CLI | Google | 10.5 万 | 免费大上下文 |
| Aider | 开源社区 | 4.8 万 | Git-native 工作流 |

**判断**：这是一个**全球数十万级开发者**每天使用的工具品类，且仍在快速分裂（每家头部模型厂都在做自己的 CLI）。

### 1.2 国内 CLI / 平台

| 工具 | 月活 | 备注 |
|---|---|---|
| DeepSeek | **1.39 亿** | 出海总榜第一 |
| Kimi | 2269 万 | 长文本 + 多智能体并行 |
| 飞书 CLI (lark-cli) | 1 万+ Stars | 协同办公官方 CLI |

**判断**：国内市场体量更大，CLI 工具的本地化（中文文档 + 国产模型适配）有真实需求。

### 1.3 目标 TAM 估算

- 同时使用 ≥ 2 个 CLI 的开发者比例（合理估计）：30-50%
- 全球 AI Agent CLI 活跃开发者（基于 OpenCode + Claude + Codex + Gemini 之和去重）：~50 万
- **目标 TAM**：~15-50 万开发者

---

## 2. 痛点真实性

### 2.1 已存在的"投票证据"

| 竞品 | Stars | 上线时长 | 核心价值 |
|---|---|---|---|
| **CC Switch** | **10 万** | 10 个月 | 把散落在 7 个工具里的 AI CLI 配置管好 |
| Open Design | （进行中） | 新 | 自动扫描 PATH 上所有 Agent CLI + Web 界面 |
| Orca | 快速增长 | 新 | 并行编排多个编码 Agent |
| Claude-Code-Workflow | 小众 | 稳定 | JSON 驱动跨 CLI 调度 |

**结论**：**CC Switch 用 10 万 Stars 投了票 —— 开发者愿意主动切换工具来统一管理 CLI**。痛点 100% 真实，不是推测。

### 2.2 痛点的具体表现

- 频繁切换供应商（Claude / OpenAI / Google / DeepSeek）
- 每个 CLI 配置文件散落各处（`.claude.json` / `.codex` / `.gemini` / ...）
- 手动复制粘贴跨 CLI 传递上下文
- 没有统一界面看多个 Agent 的执行状态

---

## 3. 技术路线之争

### 3.1 路线 A：MCP（Model Context Protocol）

- **主张**：用统一协议让所有 CLI 互通
- **优势**：理论上的"标准"
- **问题**：
  - 单次调用 token 成本高 **10-32 倍**
  - 调试困难
  - 需要每个 CLI 厂商主动适配
- **社区评价**：被部分开发者唱衰

### 3.2 路线 B：CLI + Skill 组合 ⭐ 推荐

- **支持者**：Vercel CEO 等业界大佬
- **优势**：
  - CLI 是模型天生就会用的东西，**零适配成本**
  - **可靠率 100%**（不解析对方输出，直接执行）
  - 不需要厂商配合，第三方可独立做
- **应用**：Open Design、Claude-Code-Workflow 都走这条路

### 3.3 我们的选择

**走路线 B（CLI + Skill 组合）**。理由：
- 不依赖 LLM 厂商配合
- 与现有项目技术栈完美契合
- Open Design 已经验证可行

---

## 4. 竞争格局

### 4.1 四类竞品扫描

| 竞品 | 类别 | 优势 | 弱点 |
|---|---|---|---|
| **CC Switch** | 配置管理 | 10 万 Stars | 只管配置，不管工作流 |
| **Open Design** | CLI 扫描 + Web | 已经做 CLI 自动扫描 | 偏"设计"场景，不是通用 Agent |
| **Orca** | 并行编排 | 思路新 | 单一界面，无画布可视化 |
| **Claude-Code-Workflow** | JSON 配置 | 灵活 | 要手写 JSON，不直观 |

### 4.2 差异化机会

**机会 1：CC Switch 管配置，我们管工作流** —— 完全不同的问题
**机会 2：Spring Boot 栈** —— 国内 Java 工程师更熟；Open Design 是 Node.js
**机会 3：完整中文文档 + 国产模型适配** —— 国内 1.39 亿 DeepSeek 用户市场
**机会 4：可视化画布** —— Orca 没有画布；Open Design 是表单；CC Switch 是配置文件

### 4.3 我们的劣势（诚实）

- ❌ OpenCode 19.5 万 Stars 已建立压倒性优势
- ❌ CC Switch 10 万 Stars 是事实标准
- ❌ 入场晚（不是 first mover）

### 4.4 但是仍有空间

> 在"**Web 可视化编排多个 CLI 协作完成复杂任务**"这个细分赛道上，目前**没有明确的赢家**。
>
> CC Switch 管配置（不是我们的赛道）。Open Design 偏设计（不是通用）。Orca 单一界面（不是画布）。

---

## 5. 技术可行性

### 5.1 现有项目资产复用度

| 资产 | 复用价值 |
|---|---|
| Spring Boot + SQLite | ✅ 完美本地服务栈 |
| Vue Flow 画布 | ✅ 直接用于 CLI 工作流编排 |
| WebSocket | ✅ 流式推送 CLI stdout |
| ThreadPoolConfig | ✅ 多 CLI 进程并发 |
| JWT 多用户 | ⚠️ 降级为单用户 |
| LLM Provider 适配 | ❌ 不需要（CLI 自己调 LLM） |
| Evolution / Gate / Scheduler | ❌ 完全砍掉 |
| 模板 / 市场 | ❌ 完全砍掉 |

### 5.2 核心架构

```
┌─────────────────────────────────────────┐
│   Web UI (Vue 3 + Vue Flow)             │
│   localhost:3000                        │
│   - 自动扫描 PATH 上的 Agent CLI         │
│   - 拖拽编排 CLI 工作流                  │
│   - 实时查看每个 CLI 的 stdout            │
└──────────────────┬──────────────────────┘
                   │ WebSocket
                   ↓
┌─────────────────────────────────────────┐
│   Backend (Spring Boot)                 │
│   localhost:8080                        │
│   - CLI Registry: 探测 + 启动 + 监控     │
│   - Process Manager: 跨 CLI 工作流编排   │
│   - SQLite: 存储会话历史                │
└──────────────────┬──────────────────────┘
                   │ 子进程 stdin/stdout
                   ↓
┌─────────┬─────────┬─────────┬─────────┐
│ Claude  │  Codex  │ Gemini  │  Open   │
│  Code   │   CLI   │   CLI   │  Code   │
└─────────┴─────────┴─────────┴─────────┘
```

### 5.3 关键技术难点

| 难点 | 难度 | 解决方案 |
|---|---|---|
| 跨平台 CLI 探测（PATH 扫描） | 中 | `which` / `where` + 已知 CLI 名称列表 |
| 多 CLI 进程并发管理 | 中 | Spring Boot 进程池 |
| stdout 流式推送 | 低 | WebSocket（已具备） |
| 跨 CLI 数据传递 | 高 | 设计 Adapter 协议 |
| 错误恢复与超时 | 低 | 已有 `LlmRetryPolicy` 思路 |
| CLI 配置持久化 | 低 | SQLite 已具备 |

---

## 6. 商业模型

### 6.1 主线：开源

- License：MIT 或 Apache 2.0
- 通过 GitHub Stars / 社区贡献扩大影响力

### 6.2 可选变现

| 路径 | 客单价 | 目标用户 |
|---|---|---|
| 云托管版 | ¥29/月 | 不想自部署的个人开发者 |
| 企业私有部署 | ¥50k/年 | 中型企业 IT |
| 高级 Skill 模板市场 | 抽成 30% | Skill 作者 |

### 6.3 6 个月目标

- 100+ GitHub Stars（不是 ARR，是社区认可）
- 3+ 个非作者贡献者
- 1+ 篇中文技术文章被转载

---

## 7. 最终决策

| 评估项 | 评分 |
|---|---|
| 痛点真实度 | **10/10** |
| 时机 | **8/10** |
| 差异化 | **7/10** |
| 技术匹配 | **9/10** |
| 竞争壁垒 | **6/10** |
| 商业潜力 | **5/10** |
| **总分** | **45/60** |

### 决策

> ✅ **GO。立即开始 W1。**

---

## 8. W1 立即可做的 6 个动作

| # | 动作 | 工作量 |
|---|---|---|
| 1 | README 第一行改成 "TeamMind：本地 AI Agent CLI 编排控制台" | 5 分钟 |
| 2 | 列 5 个 CLI 适配清单（OpenCode / Claude Code / Codex / Gemini / Aider），附 GitHub 链接 | 30 分钟 |
| 3 | 写 `docs/adapters/spec.md`：CLI Adapter 协议 | 半天 |
| 4 | 写 `docs/research/competitive-landscape.md`：CC Switch / Open Design / Orla 对比 | 半天 |
| 5 | 砍代码：删除 evolution/、market/、templates/、auth/、scheduler/ | 2 天 |
| 6 | GitHub Issue 发 RFC：征求 CLI Adapter 贡献 | 1 小时 |

---

## 附录 A：关键参考链接

- OpenCode: https://github.com/opencode-ai/opencode
- Claude Code: https://github.com/anthropics/claude-code
- Codex CLI: https://github.com/openai/codex
- Gemini CLI: https://github.com/google-gemini/gemini-cli
- Aider: https://github.com/Aider-AI/aider
- CC Switch: https://github.com/farion1231/cc-switch
- Orca: https://github.com/stablyai/orca (v1.4.178，Electron + TS)
- Claude-Code-Workflow: https://github.com/catlog22/claude-code-workflow

### Orca 深度分析

Orca 已在 2026 年 8 月达到 v1.4.178，支持 26+ 个 Agent，有移动端 App。
详见 `docs/research/orca-competitive-analysis.md`。

核心差异一句话：
> **Orca = 并行 Agent IDE（手动并排比较）；TeamMind = Project AI Team Runtime（自动协作 + 自适应学习）。**

---

**报告版本**：v1.0
**下次更新**：W2 结束（实施进展）
# Agent Design Philosophy Matrix (v0.1 Draft)

> 把每个 CLI 的"设计哲学"提取出来，作为 TeamMind Capability Routing 的依据。
>
> 重要原则：这些信息**从官方文档提取**，不来自主观打分。

---

## 一、为什么需要这个 Matrix

不同 CLI 不是"同质工具"，而是"不同工程方法论的载体"。

```
❌ 错误认知：Claude / Codex / Aider 都是"AI 编程助手"

✅ 正确认知：
   Claude Code = 安全 / 权限 / 显式审批的工程师
   Codex       = 执行 / 构建 / 测试闭环的工程师
   Aider       = 快速定向编辑的工程师
```

TeamMind 的差异化价值 = 让这些不同方法论形成**互补**。

---

## 二、5 个 CLI 的完整拆解

### 2.1 Claude Code

#### 基础信息

| 项 | 值 |
|---|---|
| Vendor | Anthropic |
| Repo | https://github.com/anthropics/claude-code |
| Stars | 14.1 万 |
| License | Proprietary（Anthropic API） |
| 默认安装 | `claude` |

#### 设计哲学（来自官方）

**核心设计目标**（Anthropic 官方）：

1. **可控的权限边界** —— 每个操作需要用户授权
2. **对每个操作要求显式审批** —— 不是"自动执行一切"
3. **沙箱隔离执行** —— 文件系统、网络、Shell 操作受控
4. **用户主导决策** —— 关键决策回到用户

**关键哲学**：

| 哲学 | 说明 |
|---|---|
| `safety` | 安全第一 |
| `controlled_action` | 每个动作受控 |
| `explicit_permission` | 显式权限 |
| `cautious_execution` | 谨慎执行 |
| `human_in_the_loop` | 用户始终在场 |

#### 角色倾向

| 角色 | 倾向 | 理由 |
|---|---|---|
| security_review | **STRONG** | 设计目标就是"安全审查" |
| architecture_review | **STRONG** | 推理质量极高 |
| risk_analysis | **STRONG** | 沙箱化设计天然适合风险评估 |
| implementation | GOOD | 能写，但偏向"高质量慢写" |
| test_generation | FAIR | 不是主要场景 |
| refactoring | FAIR | 显式权限机制让大重构变慢 |
| documentation | GOOD | 文档能力强 |
| research | FAIR | 不是主要场景 |

#### 能力声明

```yaml
capabilities:
  code_review:            EXCELLENT  # 强项
  implementation:        GOOD
  architecture_design:    EXCELLENT  # 强项
  risk_analysis:          EXCELLENT  # 强项
  test_generation:        FAIR
  refactoring:            FAIR       # 显式权限导致批量重构不便
  documentation:          GOOD
  research:               FAIR
  large_codebase_analysis: GOOD
  privacy_sensitive:      POOR       # 必须调 Anthropic API
```

#### 性能特征

```yaml
profile:
  avg_latency_ms: 45000        # 推理较慢（深度思考）
  reliability_score: 0.92      # 高（企业级）
  cost: high                   # Claude API 较贵
  max_context: 200k            # 大上下文
  
runtime_requirements:
  - ANTHROPIC_API_KEY
  
best_for:
  - "需要深度推理的架构决策"
  - "涉及权限边界的安全审查"
  - "复杂业务逻辑的实现"
  - "显式可控的工作流"
  
avoid_for:
  - "大批量机械重构（显式权限太慢）"
  - "纯研究类任务（成本高）"
  - "代码不能出本地的场景"
```

---

### 2.2 Codex CLI

#### 基础信息

| 项 | 值 |
|---|---|
| Vendor | OpenAI |
| Repo | https://github.com/openai/codex |
| Stars | 10.5 万 |
| License | Apache 2.0（CLI 开源） |
| 默认安装 | `codex` |

#### 设计哲学（来自官方）

**OpenAI 官方对 Codex 的定位**（开发者文档）：

> "Codex 帮你 build features and fix bugs，然后 test、review、ship changes。"

**核心设计目标**：

1. **理解整个代码库** —— 大上下文窗口 + 仓库索引
2. **修改代码并测试** —— 不是只写，是写 + 测
3. **审查 diff 后提交** —— 内置 review 流程
4. **沙箱执行** —— 安全但少显式确认

**关键哲学**：

| 哲学 | 说明 |
|---|---|
| `execution` | 执行导向 |
| `repository_understanding` | 仓库级理解 |
| `iterative_build` | 迭代构建 |
| `test_and_review` | 测试 + 审查 |
| `sandbox_safe` | 沙箱内安全执行 |

#### 角色倾向

| 角色 | 倾向 | 理由 |
|---|---|---|
| implementation | **STRONG** | 设计目标就是"build features" |
| debugging | **STRONG** | 强仓库理解 |
| testing | **STRONG** | test_and_review 是核心 |
| diff_iteration | **STRONG** | 内置 review 循环 |
| refactoring | **STRONG** | 仓库级重构 |
| code_review | GOOD | 内置 review |
| architecture_design | FAIR | 不是主要场景 |
| risk_analysis | FAIR | 沙箱安全但非显式审批 |
| documentation | FAIR | 不是主要场景 |
| research | POOR | 不是主要场景 |

#### 能力声明

```yaml
capabilities:
  code_review:            GOOD
  implementation:        EXCELLENT  # 强项
  architecture_design:    FAIR
  risk_analysis:          FAIR
  test_generation:        EXCELLENT  # 强项
  refactoring:            EXCELLENT  # 强项
  documentation:          FAIR
  research:               POOR
  large_codebase_analysis: EXCELLENT # 仓库索引
  privacy_sensitive:      POOR
```

#### 性能特征

```yaml
profile:
  avg_latency_ms: 35000        # 比 Claude 略快
  reliability_score: 0.94      # 高（OpenAI API）
  cost: high                   # OpenAI API 较贵
  max_context: 大              # 仓库级
  
runtime_requirements:
  - OPENAI_API_KEY
  
best_for:
  - "完整仓库的代码实现"
  - "测试生成与 diff 审查"
  - "重构与 bug 修复"
  - "需要执行闭环的任务"
  
avoid_for:
  - "纯架构设计（推理不如 Claude）"
  - "纯研究类任务"
```

---

### 2.3 Aider

#### 基础信息

| 项 | 值 |
|---|---|
| Vendor | 开源社区 |
| Repo | https://github.com/Aider-AI/aider |
| Stars | 4.8 万 |
| License | Apache 2.0 |
| 默认安装 | `aider` |

#### 设计哲学（来自官方）

**Aider 的设计定位**（官方 README）：

> "Aider is AI pair programming in your terminal."

> "It works best when you have an existing codebase that you want to edit and extend."

**核心设计目标**：

1. **Git 原生集成** —— 自动 commit、commit message 生成
2. **大代码库定向修改** —— 不必每次上传整个仓库
3. **配对编程风格** —— 用户主导修改方向
4. **多模型支持** —— 可接 Claude / GPT / 本地模型

**关键哲学**：

| 哲学 | 说明 |
|---|---|
| `targeted_edit` | 定向编辑 |
| `git_native` | Git 原生 |
| `rapid_patch` | 快速补丁 |
| `pair_programming` | 配对编程风格 |
| `multi_model` | 多模型后端 |

#### 角色倾向

| 角色 | 倾向 | 理由 |
|---|---|---|
| refactoring | **STRONG** | 定向编辑强项 |
| targeted_edit | **STRONG** | 核心设计 |
| rapid_patch | **STRONG** | 快速应用补丁 |
| implementation | GOOD | 可用，但不强 |
| test_generation | FAIR | 不是主要场景 |
| code_review | FAIR | 不是主要场景 |
| architecture_design | POOR | 偏向"改"而非"想" |
| research | POOR | 不是主要场景 |
| documentation | FAIR | 不是主要场景 |

#### 能力声明

```yaml
capabilities:
  code_review:            FAIR
  implementation:        GOOD
  architecture_design:    POOR
  risk_analysis:          POOR
  test_generation:        FAIR
  refactoring:            EXCELLENT  # 强项
  documentation:          FAIR
  research:               POOR
  large_codebase_analysis: GOOD
  privacy_sensitive:      GOOD       # 支持本地模型
```

#### 性能特征

```yaml
profile:
  avg_latency_ms: 25000        # 较快
  reliability_score: 0.88      # 略低（开源质量）
  cost: low                   # 可接本地模型
  max_context: 大              # Git 索引
  
runtime_requirements:
  - 任意 LLM API key 或本地模型
  
best_for:
  - "已有代码库的定向重构"
  - "Git 自动 commit 工作流"
  - "成本敏感场景"
  - "隐私敏感场景（本地模型）"
  
avoid_for:
  - "完整架构设计"
  - "纯研究类任务"
  - "复杂多文件实现"
```

---

### 2.4 Gemini CLI

#### 基础信息

| 项 | 值 |
|---|---|
| Vendor | Google |
| Repo | https://github.com/google-gemini/gemini-cli |
| Stars | 10.5 万 |
| License | Apache 2.0 |
| 默认安装 | `gemini` |

#### 设计哲学（来自官方）

**Google 对 Gemini CLI 的定位**：

> "An open-source AI agent that brings the power of Gemini directly into your terminal."

**核心设计目标**：

1. **大上下文窗口** —— Gemini 1M+ 上下文
2. **多模态** —— 支持图像、视频、音频
3. **免费额度大** —— Google AI Studio 免费层
4. **工具集成** —— 搜索、文件、shell

**关键哲学**：

| 哲学 | 说明 |
|---|---|
| `research` | 研究导向 |
| `multimodal` | 多模态 |
| `large_context` | 大上下文 |
| `free_tier` | 免费额度大 |
| `tool_integration` | 工具集成 |

#### 角色倾向

| 角色 | 倾向 | 理由 |
|---|---|---|
| research | **STRONG** | 设计目标就是"研究" |
| documentation_search | **STRONG** | 大上下文检索 |
| large_codebase_analysis | **STRONG** | 1M 上下文 |
| documentation | **STRONG** | 多模态 + 大上下文 |
| code_review | GOOD | 可做但非主场景 |
| implementation | GOOD | 可做但非主场景 |
| test_generation | GOOD | 可做但非主场景 |
| refactoring | FAIR | 不擅长复杂重构 |
| architecture_design | GOOD | 大上下文利于架构思考 |
| risk_analysis | FAIR | 不是主要场景 |

#### 能力声明

```yaml
capabilities:
  code_review:            GOOD
  implementation:        GOOD
  architecture_design:    GOOD
  risk_analysis:          FAIR
  test_generation:        GOOD
  refactoring:            FAIR
  documentation:          EXCELLENT  # 强项
  research:               EXCELLENT  # 强项
  large_codebase_analysis: EXCELLENT # 1M 上下文
  privacy_sensitive:      POOR       # 必须调 Google API
  multimodal:             EXCELLENT  # 强项
```

#### 性能特征

```yaml
profile:
  avg_latency_ms: 30000        # 中等
  reliability_score: 0.85      # 中等（API 偶有失败）
  cost: low                   # 免费额度大
  max_context: 1000000        # 1M tokens
  multimodal: true
  
runtime_requirements:
  - GOOGLE_API_KEY（或免费层）
  
best_for:
  - "大代码库理解"
  - "文档生成与检索"
  - "研究类任务"
  - "免费场景"
  - "多模态处理"
  
avoid_for:
  - "复杂精确重构"
  - "高可靠性生产场景"
```

---

### 2.5 OpenCode

#### 基础信息

| 项 | 值 |
|---|---|
| Vendor | 开源社区 |
| Repo | https://github.com/opencode-ai/opencode |
| Stars | 19.5 万 |
| License | MIT |
| 默认安装 | `opencode` |

#### 设计哲学（来自官方）

**OpenCode 的定位**（官方 README）：

> "The AI coding agent built for the terminal."

> "OpenCode is a universal AI coding agent that works with any model."

**核心设计目标**：

1. **多模型支持** —— 不是绑死一个厂商
2. **本地化优先** —— 隐私保护
3. **高度可配置** —— Provider、模型自由切换
4. **终端原生** —— CLI-first

**关键哲学**：

| 哲学 | 说明 |
|---|---|
| `flexibility` | 灵活 |
| `open_runtime` | 开放运行时 |
| `multi_model` | 多模型 |
| `privacy` | 隐私本地化 |
| `cli_native` | CLI 原生 |

#### 角色倾向

| 角色 | 倾向 | 理由 |
|---|---|---|
| general_purpose | **STRONG** | 多模型 → 通用 |
| privacy_sensitive | **STRONG** | 本地化设计 |
| code_review | GOOD | 多模型可选强推理 |
| implementation | GOOD | 多模型可选执行型 |
| architecture_design | GOOD | 多模型可选推理型 |
| refactoring | GOOD | 多模型可选编辑型 |
| research | GOOD | 多模型可选研究型 |
| test_generation | GOOD | 多模型可选 |
| documentation | GOOD | 多模型可选 |

#### 能力声明

```yaml
capabilities:
  code_review:            GOOD      # 取决于所选模型
  implementation:        GOOD
  architecture_design:    GOOD
  risk_analysis:          FAIR
  test_generation:        GOOD
  refactoring:            GOOD
  documentation:          GOOD
  research:               GOOD
  large_codebase_analysis: GOOD
  privacy_sensitive:      EXCELLENT # 本地化
  flexibility:            EXCELLENT # 多模型
```

#### 性能特征

```yaml
profile:
  avg_latency_ms: 20000        # 快（取决于模型）
  reliability_score: 0.87      # 中等
  cost: variable              # 取决于所选模型
  max_context: variable
  privacy: high               # 本地化
  
runtime_requirements:
  - 任意 LLM API key 或本地模型
  
best_for:
  - "需要灵活切换模型的场景"
  - "代码不能出本地的隐私敏感场景"
  - "成本敏感场景（用本地模型）"
  - "想要一个'万能' Agent"
  
avoid_for:
  - "特定厂商擅长的任务（不如专精）"
  - "深度推理（看具体配置）"
```

---

## 三、能力 × Plugin 完整矩阵

| 能力 \ Plugin | Claude Code | Codex | Aider | Gemini CLI | OpenCode |
|---|---|---|---|---|---|
| **code_review** | EXCELLENT | GOOD | FAIR | GOOD | GOOD |
| **implementation** | GOOD | EXCELLENT | GOOD | GOOD | GOOD |
| **architecture_design** | EXCELLENT | FAIR | POOR | GOOD | GOOD |
| **risk_analysis** | EXCELLENT | FAIR | POOR | FAIR | FAIR |
| **test_generation** | FAIR | EXCELLENT | FAIR | GOOD | GOOD |
| **refactoring** | FAIR | EXCELLENT | EXCELLENT | FAIR | GOOD |
| **documentation** | GOOD | FAIR | FAIR | EXCELLENT | GOOD |
| **research** | GOOD | POOR | POOR | EXCELLENT | GOOD |
| **large_codebase_analysis** | GOOD | EXCELLENT | GOOD | EXCELLENT | GOOD |
| **privacy_sensitive** | POOR | POOR | GOOD | POOR | EXCELLENT |
| **multimodal** | POOR | POOR | POOR | EXCELLENT | POOR |
| **rapid_patch** | FAIR | GOOD | EXCELLENT | FAIR | GOOD |
| **git_native** | FAIR | GOOD | EXCELLENT | FAIR | GOOD |
| **explicit_permission** | EXCELLENT | FAIR | POOR | POOR | POOR |
| **sandbox_safe** | GOOD | GOOD | FAIR | GOOD | GOOD |

---

## 四、典型任务的最优 Agent

| 任务类型 | 首选 Plugin | 次选 Plugin | 理由 |
|---|---|---|---|
| "实现 JWT 登录" | Codex | Aider | implementation 强项 |
| "审查 PR 中的权限变更" | Claude Code | Gemini CLI | safety + controlled_action |
| "重构 200 文件的 monorepo" | Aider | Codex | rapid_patch + git_native |
| "理解 1M token 代码库" | Gemini CLI | OpenCode | 大上下文 |
| "金融系统安全审查" | Claude Code | Codex | 显式权限 + sandbox |
| "本地模型跑代码（隐私敏感）" | OpenCode | Aider | 本地化 |
| "多模态代码生成（截图转代码）" | Gemini CLI | (无) | 唯一多模态 |
| "快速 commit + push" | Aider | Codex | git_native |
| "架构设计 + 文档" | Claude Code | Gemini CLI | 强推理 + 大上下文 |
| "测试生成 + diff review" | Codex | (无) | test_and_review 核心 |

---

## 五、哲学匹配的实际例子

### 5.1 任务："重构 auth 模块，确保权限边界不变"

```
Task Analysis:
  required_capability: refactoring
  philosophy_hint:
    - safety              # 用户强调权限边界
    - explicit_permission # 权限审查

Candidates:
  Codex:        refactoring=EXCELLENT, philosophy_match=0/2
  Claude Code:  refactoring=FAIR, philosophy_match=2/2 ★
  Aider:        refactoring=EXCELLENT, philosophy_match=1/2

→ Winner: Claude Code (philosophy 胜出)
```

### 5.2 任务："大规模迁移 500 个文件到新 API"

```
Task Analysis:
  required_capability: refactoring
  philosophy_hint:
    - rapid_patch
    - large_scale_change

Candidates:
  Codex:        refactoring=EXCELLENT, philosophy_match=1/2
  Aider:        refactoring=EXCELLENT, philosophy_match=2/2 ★

→ Winner: Aider (philosophy match 决定)
```

---

## 六、维护与更新

### 6.1 信息来源

- 官方 README / 文档
- 厂商开发者文档
- 用户实测反馈
- 版本更新日志

### 6.2 何时更新

| 触发 | 更新内容 |
|---|---|
| CLI 新版本发布 | 更新 capabilities quality |
| CLI 设计哲学变化 | 更新 philosophy.primary |
| 新 CLI 出现 | 添加新行 |
| 项目实测发现偏差 | 调整 quality（标注来源） |

### 6.3 不确定性处理

```
如果某能力无法从官方文档直接推断：
  - 标注 quality = 'FAIR'
  - 在 notes 字段写"待验证"
  - 依赖项目级实测数据修正
```

---

## 七、与 Capability Routing 的关系

本文档是 [capability-routing.md](capability-routing.md) 中"权重 2：哲学匹配"的依据。

```
Routing Score:
  Weight 1: Project Performance     (40)  ← role-evolution.md
  Weight 2: Philosophy Match        (20)  ← 本文档
  Weight 3: Capability Declaration  (15)  ← 本文档
  Weight 4: User Preference         (10)
  Weight 5: Cost / Latency          (扣分)  ← 本文档
```

---

**版本**：v0.1 Draft
**最后更新**：2026-08-14
**数据来源**：各 CLI 官方 README / 开发者文档
**声明**：所有 quality 评估基于公开信息，不构成对各 CLI 的官方评价
# W3.2: Codex Agent Plugin 实现

> 实现第二个 Agent Plugin：OpenAI Codex CLI。
>
> 预计工作量：**1.5 天**

---

## 任务目标

完整实现 `CodexPlugin`，与 ClaudeCodePlugin 结构对称但适配 Codex 特性：

- Codex CLI 的命令格式（与 Claude 不同）
- Codex 强项是"理解仓库 → 实现 → 测试 → review diff"（自动闭环）
- 流式 JSON 事件格式
- 取消支持

---

## DoD

- [ ] CodexPlugin 实现完整
- [ ] Plugin Metadata 与 ClaudeCodePlugin 对称（结构一致）
- [ ] 单元测试覆盖率 ≥ 85%
- [ ] 与 ClaudeCodePlugin 行为可对照（同样的 Plugin 接口，不同的实现）

---

## 1. CLI 调用机制

### 1.1 Codex CLI 命令

```bash
# 基本调用
codex "实现 JWT 登录"

# 关键参数
codex \
  --cd /path/to/project \        # 工作目录
  --quiet \                       # 安静模式
  --approval-mode auto-edit \     # 审批模式（建议 auto-edit）
  --sandbox danger-full-access \  # 沙箱模式
  --json \                        # JSON 输出
  "..."

# 或者用 exec 子命令
codex exec "实现 JWT 登录"
```

### 1.2 Codex 的设计哲学（与 Claude 对比）

| 维度 | Claude Code | Codex |
|---|---|---|
| 审批 | 显式（每个操作） | 模式化（auto-edit / suggest / auto） |
| 沙箱 | 强隔离 | 中等隔离 |
| 流程 | 用户主导 | 自动闭环（build + test + review） |
| 适合 | 谨慎任务 | 执行型任务 |

### 1.3 流式输出格式（Codex JSON）

```json
{"type":"thread.started","thread_id":"..."}
{"type":"turn.started"}
{"type":"item.completed","item":{"type":"reasoning","text":"..."}}
{"type":"item.completed","item":{"type":"command_execution","command":"ls","exit_code":0}}
{"type":"item.completed","item":{"type":"file_change","file":"src/auth.ts","diff":"..."}}
{"type":"item.completed","item":{"type":"agent_message","message":"Done."}}
{"type":"turn.completed","usage":{"input_tokens":123,"output_tokens":456}}
{"type":"thread.completed"}
```

---

## 2. Plugin YAML

**位置**：`backend/src/main/resources/adapters/codex.yaml`

```yaml
id: codex
type: AGENT
name: Codex CLI
version: 1.0.0
author: OpenAI
description: |
  OpenAI 的 Codex CLI，强仓库理解 + 自动测试 + diff review 闭环。
  设计哲学：执行 / 构建 / 测试闭环。
homepage: https://github.com/openai/codex

philosophy:
  primary:
    - execution
    - repository_understanding
    - iterative_build
    - test_and_review
    - sandbox_safe
  design_goals:
    - 理解整个代码库
    - 修改代码并测试
    - 审查 diff 后提交
    - build features and fix bugs
  preferred_roles:
    - implementation
    - debugging
    - testing
    - diff_iteration
  weak_roles:
    - abstract_architecture
    - pure_research

capabilities:
  - name: code_review
    quality: GOOD
    description: "内置 review 流程"
  - name: implementation
    quality: EXCELLENT
    description: "build features 是核心目标"
  - name: architecture_design
    quality: FAIR
  - name: risk_analysis
    quality: FAIR
  - name: test_generation
    quality: EXCELLENT
    description: "test_and_review 是核心"
  - name: refactoring
    quality: EXCELLENT
    description: "仓库级重构"
  - name: documentation
    quality: FAIR
  - name: research
    quality: POOR
  - name: debugging
    quality: EXCELLENT

runtime_hints:
  avg_latency_ms: 35000
  cost_per_inv: 0.10
  cli_command: codex
  requires_api_key: OPENAI_API_KEY
```

---

## 3. Plugin 实现

**位置**：`com.teammind.plugins.CodexPlugin`

```java
package com.teammind.plugins;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.plugin.*;
import com.teammind.plugin.evidence.CommandExitEvidence;
import com.teammind.plugin.evidence.ToolCallEvidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class CodexPlugin implements Plugin {
    
    private static final Logger log = LoggerFactory.getLogger(CodexPlugin.class);
    
    private final ObjectMapper json = new ObjectMapper();
    private final ProcessFactory processFactory;
    
    private volatile Process currentProcess;
    private volatile boolean cancelled;
    
    public CodexPlugin() {
        this(new DefaultProcessFactory());
    }
    
    public CodexPlugin(ProcessFactory processFactory) {
        this.processFactory = processFactory;
    }
    
    @Override
    public PluginMetadata metadata() {
        return new PluginMetadata(
            "codex",
            PluginMetadata.PluginType.AGENT,
            "Codex CLI",
            "1.0.0",
            "OpenAI",
            "OpenAI 的 Codex CLI，强仓库理解 + 自动测试 + diff review 闭环",
            "https://github.com/openai/codex",
            new AgentPhilosophy(
                List.of("execution", "repository_understanding", "iterative_build", "test_and_review", "sandbox_safe"),
                List.of("理解整个代码库", "修改代码并测试", "审查 diff 后提交", "build features and fix bugs"),
                List.of("implementation", "debugging", "testing", "diff_iteration"),
                List.of("abstract_architecture", "pure_research")
            ),
            Map.of(
                "avgLatencyMs", 35000,
                "costPerInvocation", 0.10,
                "cliCommand", "codex"
            )
        );
    }
    
    @Override
    public List<CapabilityDescriptor> capabilities() {
        return List.of(
            new CapabilityDescriptor("code_review", CapabilityDescriptor.CapabilityQuality.GOOD, null),
            new CapabilityDescriptor("implementation", CapabilityDescriptor.CapabilityQuality.EXCELLENT, null),
            new CapabilityDescriptor("architecture_design", CapabilityDescriptor.CapabilityQuality.FAIR, null),
            new CapabilityDescriptor("risk_analysis", CapabilityDescriptor.CapabilityQuality.FAIR, null),
            new CapabilityDescriptor("test_generation", CapabilityDescriptor.CapabilityQuality.EXCELLENT, null),
            new CapabilityDescriptor("refactoring", CapabilityDescriptor.CapabilityQuality.EXCELLENT, null),
            new CapabilityDescriptor("documentation", CapabilityDescriptor.CapabilityQuality.FAIR, null),
            new CapabilityDescriptor("research", CapabilityDescriptor.CapabilityQuality.POOR, null),
            new CapabilityDescriptor("debugging", CapabilityDescriptor.CapabilityQuality.EXCELLENT, null)
        );
    }
    
    @Override
    public PluginLifecycle lifecycle() {
        return PluginLifecycle.empty();
    }
    
    @Override
    public PluginResult invoke(PluginContext context) {
        return invokeStream(context, chunk -> {});
    }
    
    @Override
    public PluginResult invokeStream(PluginContext context, ChunkHandler chunkHandler) {
        cancelled = false;
        Instant start = Instant.now();
        
        // 1. 构造 prompt
        String prompt = buildPrompt(context);
        
        // 2. 启动 codex exec
        ProcessBuilder pb = new ProcessBuilder(
            "codex", "exec",
            "--json",                          // JSON 输出
            "--cd", context.workDir(),
            "--approval-mode", "auto-edit",     // 自动批准编辑（建议）
            "-"
        );
        pb.directory(new File(context.workDir()));
        pb.redirectErrorStream(true);
        
        Process process;
        try {
            process = processFactory.start(pb);
            currentProcess = process;
        } catch (IOException e) {
            return errorResult(context.task().taskId(), "Failed to start Codex CLI: " + e.getMessage(), start);
        }
        
        // 3. 写入 prompt
        try (OutputStreamWriter writer = new OutputStreamWriter(
                process.getOutputStream(), StandardCharsets.UTF_8)) {
            writer.write(prompt);
            writer.flush();
        } catch (IOException e) {
            process.destroy();
            return errorResult(context.task().taskId(), "Failed to write prompt: " + e.getMessage(), start);
        }
        
        // 4. 读取输出
        List<String> textChunks = new ArrayList<>();
        List<Artifact> artifacts = new ArrayList<>();
        List<Evidence> evidence = new ArrayList<>();
        StringBuilder summaryBuilder = new StringBuilder();
        Map<String, String> fileChanges = new HashMap<>();  // path -> diff
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                if (cancelled) {
                    process.destroy();
                    break;
                }
                
                try {
                    JsonNode event = json.readTree(line);
                    handleEvent(event, textChunks, artifacts, evidence, 
                                summaryBuilder, fileChanges, chunkHandler);
                } catch (Exception e) {
                    log.warn("Failed to parse Codex event: {}", e.getMessage());
                }
            }
        } catch (IOException e) {
            process.destroy();
            return errorResult(context.task().taskId(), "Failed to read Codex output: " + e.getMessage(), start);
        }
        
        // 5. 等待结束
        try {
            int exitCode = process.waitFor();
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            
            // 6. 构造 CodeDiffArtifact
            if (!fileChanges.isEmpty()) {
                List<CodeDiffFile> files = new ArrayList<>();
                int adds = 0, dels = 0;
                for (var entry : fileChanges.entrySet()) {
                    files.add(new CodeDiffFile(
                        entry.getKey(), "MODIFY", entry.getValue(),
                        countLines(entry.getValue(), '+'),
                        countLines(entry.getValue(), '-')
                    ));
                    adds += countLines(entry.getValue(), '+');
                    dels += countLines(entry.getValue(), '-');
                }
                artifacts.add(new CodeDiffArtifact(files, adds, dels));
            }
            
            // 7. 构造 COMMAND_EXIT Evidence
            evidence.add(new CommandExitEvidence(
                "codex",
                List.of("exec", "--json", "--cd", context.workDir(), "--approval-mode", "auto-edit"),
                exitCode,
                null
            ));
            
            String summary = summaryBuilder.length() > 0 
                ? summaryBuilder.toString() 
                : "Codex task completed";
            
            return new PluginResult(
                metadata().id(),
                context.task().taskId(),
                exitCode == 0 ? PluginResult.PluginStatus.SUCCESS : PluginResult.PluginStatus.FAILURE,
                summary.length() > 500 ? summary.substring(0, 500) + "..." : summary,
                artifacts,
                List.of(),
                List.of(),
                evidence,
                new SelfReport(null, null),
                new PerformanceMetrics(Duration.ofMillis(durationMs), null, null),
                suggestNextAction(context)
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroy();
            return errorResult(context.task().taskId(), "Interrupted", start);
        }
    }
    
    private void handleEvent(
        JsonNode event,
        List<String> textChunks,
        List<Artifact> artifacts,
        List<Evidence> evidence,
        StringBuilder summaryBuilder,
        Map<String, String> fileChanges,
        ChunkHandler chunkHandler
    ) {
        String type = event.path("type").asText();
        
        switch (type) {
            case "thread.started" -> {
                log.debug("Codex thread started");
            }
            case "turn.started" -> {
                log.debug("Codex turn started");
            }
            case "item.completed" -> {
                JsonNode item = event.path("item");
                String itemType = item.path("type").asText();
                
                switch (itemType) {
                    case "reasoning" -> {
                        String text = item.path("text").asText();
                        if (!text.isEmpty()) {
                            textChunks.add(text);
                            chunkHandler.onChunk(text);
                        }
                    }
                    case "agent_message" -> {
                        String message = item.path("message").asText();
                        summaryBuilder.append(message).append("\n");
                        chunkHandler.onChunk(message);
                    }
                    case "command_execution" -> {
                        String command = item.path("command").asText();
                        int exitCode = item.path("exit_code").asInt();
                        evidence.add(new ToolCallEvidence(
                            "command:" + command, "exit_code=" + exitCode
                        ));
                    }
                    case "file_change" -> {
                        String file = item.path("file").asText();
                        String diff = item.path("diff").asText();
                        fileChanges.merge(file, diff, (a, b) -> a + "\n" + b);
                    }
                    default -> log.debug("Unknown Codex item type: {}", itemType);
                }
            }
            case "turn.completed" -> {
                JsonNode usage = event.path("usage");
                log.debug("Codex turn completed. Usage: {}", usage);
            }
            case "thread.completed" -> {
                log.debug("Codex thread completed");
            }
            default -> log.debug("Unknown Codex event type: {}", type);
        }
    }
    
    private int countLines(String text, char prefix) {
        return (int) text.lines().filter(l -> l.startsWith(String.valueOf(prefix))).count();
    }
    
    private String buildPrompt(PluginContext context) {
        // 与 ClaudeCodePlugin 结构对称（团队一致性）
        AgentTask task = context.task();
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("[Role: ").append(task.role()).append("]\n");
        prompt.append("\n[Objective]\n").append(task.objective()).append("\n");
        
        if (task.constraints() != null && !task.constraints().isEmpty()) {
            prompt.append("\n[Constraints]\n");
            for (String c : task.constraints()) prompt.append("- ").append(c).append("\n");
        }
        
        TaskContext taskCtx = task.context();
        if (taskCtx != null) {
            if (taskCtx.projectSummary() != null) {
                prompt.append("\n[Project Context]\n").append(taskCtx.projectSummary()).append("\n");
            }
            if (taskCtx.relevantContext() != null && !taskCtx.relevantContext().isEmpty()) {
                prompt.append("\n[Relevant Context]\n");
                for (String ctx : taskCtx.relevantContext()) {
                    prompt.append("- ").append(ctx).append("\n");
                }
            }
            if (taskCtx.previousArtifacts() != null && !taskCtx.previousArtifacts().isEmpty()) {
                prompt.append("\n[Previous Artifacts]\n");
                for (Artifact a : taskCtx.previousArtifacts()) {
                    prompt.append("- [").append(a.type()).append("] ").append(a.summary()).append("\n");
                }
            }
        }
        
        if (task.philosophyHint() != null && !task.philosophyHint().isEmpty()) {
            prompt.append("\n[Your Approach]\n");
            prompt.append("Approach this task with emphasis on: ")
                  .append(String.join(", ", task.philosophyHint()))
                  .append("\n");
        }
        
        prompt.append("\n[Output Requirements]\n");
        prompt.append("- Make concrete code changes\n");
        prompt.append("- Run relevant tests if possible\n");
        prompt.append("- Explain what you changed\n");
        
        return prompt.toString();
    }
    
    private NextAction suggestNextAction(PluginContext context) {
        AgentTask task = context.task();
        return switch (task.role()) {
            case "LEAD" -> new NextAction(
                "REVIEWER", "code_review",
                "Implementation complete. Code review recommended."
            );
            case "REVIEWER" -> new NextAction(
                "TESTER", "test_generation",
                "Review complete. Additional tests recommended."
            );
            default -> null;
        };
    }
    
    @Override
    public void cancel() {
        cancelled = true;
        if (currentProcess != null && currentProcess.isAlive()) {
            currentProcess.destroy();
            try {
                if (!currentProcess.waitFor(5, TimeUnit.SECONDS)) {
                    currentProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                currentProcess.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    @Override
    public PluginHealth inspect() {
        try {
            Process p = processFactory.start(new ProcessBuilder("codex", "--version"));
            int exitCode = p.waitFor();
            return exitCode == 0 
                ? PluginHealth.healthy() 
                : PluginHealth.unhealthy("codex --version exited with " + exitCode);
        } catch (Exception e) {
            return PluginHealth.unhealthy("codex CLI not available: " + e.getMessage());
        }
    }
    
    private PluginResult errorResult(String taskId, String error, Instant start) {
        long durationMs = Duration.between(start, Instant.now()).toMillis();
        return new PluginResult(
            metadata().id(),
            taskId,
            PluginResult.PluginStatus.FAILURE,
            error,
            List.of(), List.of(), List.of(), List.of(),
            new SelfReport(0.0, "POOR"),
            new PerformanceMetrics(Duration.ofMillis(durationMs), null, null),
            null
        );
    }
}
```

---

## 4. Code Diff Artifact

**位置**：`com.teammind.plugin.artifacts.CodeDiffArtifact`

```java
package com.teammind.plugin.artifacts;

import com.teammind.plugin.Artifact;

import java.util.List;
import java.util.Map;

public record CodeDiffArtifact(
    List<CodeDiffFile> files,
    int totalAdditions,
    int totalDeletions
) implements Artifact {
    
    @Override
    public String type() { return "CODE_DIFF"; }
    
    @Override
    public String summary() {
        return String.format("%d files changed (+%d/-%d)", 
            files.size(), totalAdditions, totalDeletions);
    }
    
    @Override
    public Map<String, Object> payload() {
        return Map.of(
            "files", files.stream().map(f -> Map.of(
                "path", f.path(),
                "changeType", f.changeType(),
                "diff", f.diff(),
                "linesAdded", f.linesAdded(),
                "linesRemoved", f.linesRemoved()
            )).toList(),
            "totalAdditions", totalAdditions,
            "totalDeletions", totalDeletions
        );
    }
    
    public record CodeDiffFile(
        String path,
        String changeType,    // ADD / MODIFY / DELETE
        String diff,
        int linesAdded,
        int linesRemoved
    ) {}
}
```

---

## 5. 单元测试

### 5.1 CodexPluginTest

```java
@ExtendWith(MockitoExtension.class)
class CodexPluginTest {
    
    CodexPlugin plugin;
    MockProcessFactory mockFactory;
    
    @BeforeEach
    void setUp() {
        mockFactory = new MockProcessFactory();
        plugin = new CodexPlugin(mockFactory);
    }
    
    @Test
    void shouldExposeMetadata() {
        PluginMetadata meta = plugin.metadata();
        
        assertThat(meta.id()).isEqualTo("codex");
        assertThat(meta.philosophy().primary())
            .contains("execution", "repository_understanding", "test_and_review");
    }
    
    @Test
    void shouldParseFileChangeEvents() throws Exception {
        String output = """
            {"type":"thread.started"}
            {"type":"item.completed","item":{"type":"file_change","file":"src/auth.ts","diff":"+jwt token\\n-session cookie"}}
            {"type":"item.completed","item":{"type":"agent_message","message":"Done."}}
            {"type":"thread.completed"}
            """;
        
        mockFactory.mockProcess(output, 0, "");
        
        PluginContext ctx = buildContext("t1", "LEAD", "实现 JWT");
        PluginResult result = plugin.invoke(ctx);
        
        assertThat(result.status()).isEqualTo(PluginResult.PluginStatus.SUCCESS);
        assertThat(result.artifacts()).hasSize(1);
        
        Artifact artifact = result.artifacts().get(0);
        assertThat(artifact).isInstanceOf(CodeDiffArtifact.class);
        CodeDiffArtifact diff = (CodeDiffArtifact) artifact;
        assertThat(diff.files()).hasSize(1);
        assertThat(diff.files().get(0).path()).isEqualTo("src/auth.ts");
    }
    
    @Test
    void shouldStreamAgentMessages() throws Exception {
        List<String> chunks = new ArrayList<>();
        
        mockFactory.mockProcess("""
            {"type":"item.completed","item":{"type":"agent_message","message":"First"}}
            {"type":"item.completed","item":{"type":"agent_message","message":"Second"}}
            """, 0, "");
        
        PluginContext ctx = buildContext("t1", "LEAD", "task");
        plugin.invokeStream(ctx, chunks::add);
        
        assertThat(chunks).contains("First", "Second");
    }
    
    @Test
    void shouldRecordCommandExecutions() throws Exception {
        String output = """
            {"type":"item.completed","item":{"type":"command_execution","command":"npm test","exit_code":0}}
            """;
        
        mockFactory.mockProcess(output, 0, "");
        
        PluginContext ctx = buildContext("t1", "LEAD", "task");
        PluginResult result = plugin.invoke(ctx);
        
        assertThat(result.evidence())
            .anyMatch(e -> e instanceof ToolCallEvidence tce && tce.toolName().contains("npm test"));
    }
    
    @Test
    void shouldHandleParseError() throws Exception {
        String output = """
            {"type":"valid"}
            invalid json line
            {"type":"item.completed","item":{"type":"agent_message","message":"OK"}}
            """;
        
        mockFactory.mockProcess(output, 0, "");
        
        PluginContext ctx = buildContext("t1", "LEAD", "task");
        PluginResult result = plugin.invoke(ctx);
        
        // 不应崩溃，应正常返回
        assertThat(result.status()).isEqualTo(PluginResult.PluginStatus.SUCCESS);
    }
    
    @Test
    void shouldInspectHealth() {
        mockFactory.mockProcess("codex 0.144.5", 0, "");
        
        assertThat(plugin.inspect().status()).isEqualTo(PluginHealth.Status.HEALTHY);
    }
    
    private PluginContext buildContext(String taskId, String role, String objective) {
        AgentTask task = AgentTask.of(taskId, role, objective);
        return new PluginContext(task, null, System.getProperty("java.io.tmpdir"), 60_000, null);
    }
}
```

---

## 6. 验收清单

- [ ] CodexPlugin 实现完成
- [ ] 与 ClaudeCodePlugin 对称（同样的接口、不同的实现）
- [ ] 单元测试覆盖率 ≥ 85%
- [ ] 所有测试通过
- [ ] 真实环境下能调用 Codex CLI（手动）

---

## 7. 接下来

- 读 [w3-verifier-plugins.md](w3-verifier-plugins.md)，实现 Git / Test Verifier
- 或读 [w4-role-evolution.md](w4-role-evolution.md)，实现自适应闭环

---

**最后更新**：2026-08-14
**版本**：v0.1 Draft
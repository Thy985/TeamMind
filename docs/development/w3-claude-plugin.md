# W3.1: Claude Code Agent Plugin 实现

> 实现第一个真正的 Agent Plugin：Claude Code。
>
> 预计工作量：**2 天**

---

## 任务目标

- 完整实现 `ClaudeCodePlugin`，封装 Claude Code CLI 子进程调用
- 支持流式输出
- 支持 cancel
- 完整 Plugin Metadata（philosophy + capabilities）
- 单元测试用 mock ProcessBuilder

---

## DoD

- [ ] ClaudeCodePlugin 实现所有 Plugin 接口
- [ ] 流式 chunk 能推送到前端
- [ ] 可取消正在运行的子进程
- [ ] 输出解析为 PluginResult（含 Evidence）
- [ ] Plugin Metadata 完整（参考 [agent-philosophy-matrix.md](../runtime/agent-philosophy-matrix.md)）
- [ ] 单元测试覆盖率 ≥ 85%
- [ ] 在真实环境下能调用 Claude Code CLI（手动验证）

---

## 1. Plugin Metadata（YAML 定义）

**位置**：`backend/src/main/resources/adapters/claude-code.yaml`

```yaml
id: claude-code
type: AGENT
name: Claude Code
version: 1.0.0
author: Anthropic
description: |
  Anthropic 官方的 AI Agent CLI，主打最强推理质量。
  设计哲学：安全 / 显式审批 / 沙箱执行。
homepage: https://github.com/anthropics/claude-code

philosophy:
  primary:
    - safety
    - controlled_action
    - explicit_permission
    - cautious_execution
  design_goals:
    - 可控的权限边界
    - 对每个操作要求显式审批
    - 沙箱隔离执行
    - 用户主导决策
  preferred_roles:
    - security_review
    - architecture_review
    - risk_analysis
  weak_roles:
    - bulk_refactor
    - bulk_formatting

capabilities:
  - name: code_review
    quality: EXCELLENT
    description: "显式权限审批流程天然适合安全审查"
  - name: implementation
    quality: GOOD
    description: "高质量慢写，不擅长大批量"
  - name: architecture_design
    quality: EXCELLENT
    description: "深度推理强项"
  - name: risk_analysis
    quality: EXCELLENT
    description: "沙箱化设计天然适合风险评估"
  - name: test_generation
    quality: FAIR
    description: "可生成但非主要场景"
  - name: refactoring
    quality: FAIR
    description: "显式权限机制让批量重构变慢"
  - name: documentation
    quality: GOOD
  - name: research
    quality: FAIR
  - name: security_review
    quality: EXCELLENT

runtime_hints:
  avg_latency_ms: 45000
  cost_per_inv: 0.15
  cli_command: claude
  requires_api_key: ANTHROPIC_API_KEY
```

---

## 2. CLI 调用机制

### 2.1 Claude Code CLI 命令

```bash
# 基本调用
claude --print "实现 JWT 登录功能"

# 流式输出
claude --print --output-format stream-json "..."

# 关键参数
claude \
  --print \                        # 输出到 stdout（默认 REPL）
  --output-format stream-json \    # 流式 JSON 格式
  --verbose \                      # 详细日志
  --model claude-sonnet-4 \        # 指定模型
  "..."
```

### 2.2 流式 JSON 事件格式

```json
{"type":"system","subtype":"init","cwd":"...","tools":[...]}
{"type":"assistant","message":{"content":[{"type":"text","text":"..."}]}}
{"type":"assistant","message":{"content":[{"type":"tool_use","name":"Read","input":{...}}]}}
{"type":"user","message":{"content":[{"type":"tool_result","content":"..."}]}}
{"type":"result","subtype":"success","duration_ms":1234,"total_cost_usd":0.15,"result":"..."}
```

---

## 3. Plugin 实现

**位置**：`com.teammind.plugins.ClaudeCodePlugin`

```java
package com.teammind.plugins;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.plugin.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public class ClaudeCodePlugin implements Plugin {
    
    private static final Logger log = LoggerFactory.getLogger(ClaudeCodePlugin.class);
    
    private final ObjectMapper json = new ObjectMapper();
    private final ProcessFactory processFactory;
    
    // 当前运行的进程（用于 cancel）
    private volatile Process currentProcess;
    private volatile boolean cancelled;
    
    public ClaudeCodePlugin() {
        this(new DefaultProcessFactory());
    }
    
    public ClaudeCodePlugin(ProcessFactory processFactory) {
        this.processFactory = processFactory;
    }
    
    @Override
    public PluginMetadata metadata() {
        return new PluginMetadata(
            "claude-code",
            PluginMetadata.PluginType.AGENT,
            "Claude Code",
            "1.0.0",
            "Anthropic",
            "Anthropic 官方的 AI Agent CLI，主打最强推理质量",
            "https://github.com/anthropics/claude-code",
            new AgentPhilosophy(
                List.of("safety", "controlled_action", "explicit_permission", "cautious_execution"),
                List.of("可控的权限边界", "对每个操作要求显式审批", "沙箱隔离执行", "用户主导决策"),
                List.of("security_review", "architecture_review", "risk_analysis"),
                List.of("bulk_refactor", "bulk_formatting")
            ),
            Map.of(
                "avgLatencyMs", 45000,
                "costPerInvocation", 0.15,
                "cliCommand", "claude"
            )
        );
    }
    
    @Override
    public List<CapabilityDescriptor> capabilities() {
        return List.of(
            new CapabilityDescriptor("code_review", CapabilityDescriptor.CapabilityQuality.EXCELLENT, null),
            new CapabilityDescriptor("implementation", CapabilityDescriptor.CapabilityQuality.GOOD, null),
            new CapabilityDescriptor("architecture_design", CapabilityDescriptor.CapabilityQuality.EXCELLENT, null),
            new CapabilityDescriptor("risk_analysis", CapabilityDescriptor.CapabilityQuality.EXCELLENT, null),
            new CapabilityDescriptor("test_generation", CapabilityDescriptor.CapabilityQuality.FAIR, null),
            new CapabilityDescriptor("refactoring", CapabilityDescriptor.CapabilityQuality.FAIR, null),
            new CapabilityDescriptor("documentation", CapabilityDescriptor.CapabilityQuality.GOOD, null),
            new CapabilityDescriptor("research", CapabilityDescriptor.CapabilityQuality.FAIR, null),
            new CapabilityDescriptor("security_review", CapabilityDescriptor.CapabilityQuality.EXCELLENT, null)
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
        
        // 2. 启动 CLI 子进程
        ProcessBuilder pb = new ProcessBuilder(
            "claude", "--print", "--output-format", "stream-json", "--verbose"
        );
        pb.directory(new File(context.workDir()));
        pb.redirectErrorStream(true);
        
        Process process;
        try {
            process = processFactory.start(pb);
            currentProcess = process;
        } catch (IOException e) {
            return errorResult(context.task().taskId(), "Failed to start Claude CLI: " + e.getMessage(), start);
        }
        
        // 3. 写入 prompt 到 stdin
        try (OutputStreamWriter writer = new OutputStreamWriter(
                process.getOutputStream(), StandardCharsets.UTF_8)) {
            writer.write(prompt);
            writer.flush();
        } catch (IOException e) {
            process.destroy();
            return errorResult(context.task().taskId(), "Failed to write prompt: " + e.getMessage(), start);
        }
        
        // 4. 读取流式输出
        List<String> textChunks = new ArrayList<>();
        List<Artifact> artifacts = new ArrayList<>();
        List<Evidence> evidence = new ArrayList<>();
        StringBuilder summaryBuilder = new StringBuilder();
        
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
                    handleEvent(event, textChunks, artifacts, evidence, summaryBuilder, chunkHandler);
                } catch (Exception parseError) {
                    log.warn("Failed to parse CLI event: {}", parseError.getMessage());
                }
            }
        } catch (IOException e) {
            process.destroy();
            return errorResult(context.task().taskId(), "Failed to read CLI output: " + e.getMessage(), start);
        }
        
        // 5. 等待进程结束
        try {
            int exitCode = process.waitFor();
            long durationMs = Duration.between(start, Instant.now()).toMillis();
            
            // 6. 构造 Evidence
            evidence.add(new CommandExitEvidence(
                "claude",
                List.of("--print", "--output-format", "stream-json"),
                exitCode,
                null
            ));
            
            // 7. 构造结果
            String summary = summaryBuilder.length() > 0 ? summaryBuilder.toString() : String.join("", textChunks);
            
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
        ChunkHandler chunkHandler
    ) {
        String type = event.path("type").asText();
        
        switch (type) {
            case "system" -> {
                // init 事件，可提取 cwd / tools
                log.debug("Claude system event: {}", event.path("subtype").asText());
            }
            case "assistant" -> {
                JsonNode content = event.path("message").path("content");
                if (content.isArray()) {
                    for (JsonNode item : content) {
                        String itemType = item.path("type").asText();
                        if ("text".equals(itemType)) {
                            String text = item.path("text").asText();
                            textChunks.add(text);
                            summaryBuilder.append(text);
                            chunkHandler.onChunk(text);
                        } else if ("tool_use".equals(itemType)) {
                            String toolName = item.path("name").asText();
                            JsonNode input = item.path("input");
                            log.debug("Claude tool use: {} input: {}", toolName, input);
                            // 可生成 Evidence 表示"Claude 用了这个 tool"
                            evidence.add(new ToolCallEvidence(toolName, input.toString()));
                        }
                    }
                }
            }
            case "user" -> {
                // tool_result
                log.debug("Claude tool result");
            }
            case "result" -> {
                String subtype = event.path("subtype").asText();
                if ("success".equals(subtype)) {
                    String finalResult = event.path("result").asText();
                    if (!finalResult.isEmpty()) {
                        summaryBuilder.append("\n").append(finalResult);
                    }
                    // 提取文件改动（如有）
                    extractCodeDiffArtifacts(event, artifacts);
                }
            }
            default -> log.debug("Unknown event type: {}", type);
        }
    }
    
    private void extractCodeDiffArtifacts(JsonNode resultEvent, List<Artifact> artifacts) {
        // Claude Code 在 result 事件中可能包含 modified_files / diff
        // 简化处理：留待后续版本解析
        // TODO: 根据实际 CLI 输出格式调整
    }
    
    private String buildPrompt(PluginContext context) {
        AgentTask task = context.task();
        StringBuilder prompt = new StringBuilder();
        
        // 1. Role 提示
        prompt.append("[Role: ").append(task.role()).append("]\n");
        
        // 2. 任务目标
        prompt.append("\n[Objective]\n").append(task.objective()).append("\n");
        
        // 3. 约束
        if (task.constraints() != null && !task.constraints().isEmpty()) {
            prompt.append("\n[Constraints]\n");
            for (String c : task.constraints()) {
                prompt.append("- ").append(c).append("\n");
            }
        }
        
        // 4. 项目上下文
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
        
        // 5. 哲学 hint（告诉 Claude 它是什么 role 的 Agent）
        if (task.philosophyHint() != null && !task.philosophyHint().isEmpty()) {
            prompt.append("\n[Your Approach]\n");
            prompt.append("Approach this task with emphasis on: ")
                  .append(String.join(", ", task.philosophyHint()))
                  .append("\n");
        }
        
        // 6. 输出要求
        prompt.append("\n[Output Requirements]\n");
        prompt.append("- Be concise and focused\n");
        prompt.append("- Provide concrete code changes with file paths\n");
        prompt.append("- Explain your reasoning briefly\n");
        
        return prompt.toString();
    }
    
    private NextAction suggestNextAction(PluginContext context) {
        AgentTask task = context.task();
        String role = task.role();
        
        // 根据当前 role 建议下一步
        return switch (role) {
            case "LEAD" -> new NextAction(
                "REVIEWER", "code_review",
                "Implementation complete. Code review recommended."
            );
            case "REVIEWER" -> new NextAction(
                "TESTER", "test_generation",
                "Review complete. Regression tests recommended."
            );
            default -> null;
        };
    }
    
    @Override
    public void cancel() {
        cancelled = true;
        if (currentProcess != null && currentProcess.isAlive()) {
            currentProcess.destroy();
            // 给点时间优雅退出
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
            Process p = processFactory.start(
                new ProcessBuilder("claude", "--version")
            );
            int exitCode = p.waitFor();
            if (exitCode == 0) {
                String version = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                return PluginHealth.healthy();
                // 可附 metadata: version
            } else {
                return PluginHealth.unhealthy("claude --version exited with " + exitCode);
            }
        } catch (Exception e) {
            return PluginHealth.unhealthy("claude CLI not available: " + e.getMessage());
        }
    }
    
    private PluginResult errorResult(String taskId, String error, Instant start) {
        long durationMs = Duration.between(start, Instant.now()).toMillis();
        return new PluginResult(
            metadata().id(),
            taskId,
            PluginResult.PluginStatus.FAILURE,
            error,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            new SelfReport(0.0, "POOR"),
            new PerformanceMetrics(Duration.ofMillis(durationMs), null, null),
            null
        );
    }
}
```

---

## 4. Process Factory（便于测试 mock）

**位置**：`com.teammind.plugins.ProcessFactory`

```java
package com.teammind.plugins;

import java.io.IOException;

public interface ProcessFactory {
    Process start(ProcessBuilder pb) throws IOException;
    
    class DefaultProcessFactory implements ProcessFactory {
        @Override
        public Process start(ProcessBuilder pb) throws IOException {
            return pb.start();
        }
    }
}
```

---

## 5. Evidence 类型扩展

**位置**：`com.teammind.plugin.evidence.CommandExitEvidence`

```java
package com.teammind.plugin.evidence;

import com.teammind.plugin.Evidence;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CommandExitEvidence(
    String command,
    List<String> args,
    int exitCode,
    String stdoutSummary
) implements Evidence {
    
    @Override
    public String type() { return "COMMAND_EXIT"; }
    
    @Override
    public boolean verified() { return exitCode == 0; }
    
    @Override
    public Instant verifiedAt() { return Instant.now(); }
    
    @Override
    public String verificationMethod() { return "exit_code"; }
    
    @Override
    public Map<String, Object> payload() {
        return Map.of(
            "command", command,
            "args", args,
            "exitCode", exitCode,
            "stdoutSummary", stdoutSummary != null ? stdoutSummary : ""
        );
    }
}
```

```java
package com.teammind.plugin.evidence;

import com.teammind.plugin.Evidence;

import java.time.Instant;
import java.util.Map;

public record ToolCallEvidence(
    String toolName,
    String input
) implements Evidence {
    
    @Override
    public String type() { return "TOOL_CALL"; }
    
    @Override
    public boolean verified() { return true; }
    
    @Override
    public Instant verifiedAt() { return Instant.now(); }
    
    @Override
    public String verificationMethod() { return "internal_log"; }
    
    @Override
    public Map<String, Object> payload() {
        return Map.of("toolName", toolName, "input", input);
    }
}
```

---

## 6. Chunk Handler

**位置**：`com.teammind.plugin.ChunkHandler`

```java
package com.teammind.plugin;

@FunctionalInterface
public interface ChunkHandler {
    void onChunk(String content);
    
    default void onError(String error) {}
    default void onComplete() {}
}
```

---

## 7. Plugin 自动注册

**位置**：`com.teammind.config.PluginsAutoConfig`

```java
package com.teammind.config;

import com.teammind.plugin.PluginManager;
import com.teammind.plugins.ClaudeCodePlugin;
import com.teammind.plugins.CodexPlugin;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class PluginsAutoConfig {
    
    private final PluginManager pluginManager;
    
    public PluginsAutoConfig(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }
    
    @EventListener(ApplicationReadyEvent.class)
    public void registerDefaultPlugins() {
        // 内置 Plugin 注册
        pluginManager.register(new ClaudeCodePlugin());
        pluginManager.register(new CodexPlugin());
        // ... 其他 Plugin
    }
}
```

---

## 8. 单元测试

### 8.1 ClaudeCodePluginTest

**位置**：`backend/src/test/java/com/teammind/plugins/ClaudeCodePluginTest.java`

```java
package com.teammind.plugins;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.plugin.*;
import com.teammind.plugin.evidence.CommandExitEvidence;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ClaudeCodePluginTest {
    
    ClaudeCodePlugin plugin;
    MockProcessFactory mockFactory;
    ObjectMapper json = new ObjectMapper();
    
    @BeforeEach
    void setUp() {
        plugin = new ClaudeCodePlugin();
        mockFactory = new MockProcessFactory();
        plugin = new ClaudeCodePlugin(mockFactory);
    }
    
    @Test
    void shouldExposeMetadata() {
        PluginMetadata meta = plugin.metadata();
        
        assertThat(meta.id()).isEqualTo("claude-code");
        assertThat(meta.type()).isEqualTo(PluginMetadata.PluginType.AGENT);
        assertThat(meta.philosophy().primary()).contains("safety", "controlled_action");
    }
    
    @Test
    void shouldDeclareCapabilities() {
        List<CapabilityDescriptor> caps = plugin.capabilities();
        
        assertThat(caps).extracting(CapabilityDescriptor::name)
            .contains("code_review", "implementation", "security_review");
        assertThat(caps).extracting(CapabilityDescriptor::quality)
            .contains(CapabilityDescriptor.CapabilityQuality.EXCELLENT);
    }
    
    @Test
    void shouldInvokeAndReturnResult() throws Exception {
        // Mock CLI 输出
        String output = """
            {"type":"system","subtype":"init"}
            {"type":"assistant","message":{"content":[{"type":"text","text":"I'll implement JWT auth."}]}}
            {"type":"assistant","message":{"content":[{"type":"tool_use","name":"Read","input":{"path":"auth.ts"}}]}}
            {"type":"result","subtype":"success","duration_ms":1234,"result":"Done"}
            """;
        
        MockProcess mockProcess = mockFactory.mockProcess(
            output, 0, ""  // stdout, exit code, stderr
        );
        
        PluginContext ctx = buildContext("t1", "LEAD", "实现 JWT auth");
        PluginResult result = plugin.invoke(ctx);
        
        assertThat(result.status()).isEqualTo(PluginResult.PluginStatus.SUCCESS);
        assertThat(result.summary()).contains("JWT auth");
        assertThat(result.evidence()).hasSizeGreaterThan(1);
        assertThat(result.evidence()).anyMatch(e -> e instanceof CommandExitEvidence);
    }
    
    @Test
    void shouldStreamChunks() throws Exception {
        List<String> chunks = new ArrayList<>();
        ChunkHandler handler = chunks::add;
        
        mockFactory.mockProcess("""
            {"type":"assistant","message":{"content":[{"type":"text","text":"First chunk"}]}}
            {"type":"assistant","message":{"content":[{"type":"text","text":"Second chunk"}]}}
            """, 0, "");
        
        PluginContext ctx = buildContext("t1", "LEAD", "task");
        plugin.invokeStream(ctx, handler);
        
        assertThat(chunks).containsExactly("First chunk", "Second chunk");
    }
    
    @Test
    void shouldReturnFailureOnNonZeroExit() throws Exception {
        mockFactory.mockProcess("Error occurred", 1, "");
        
        PluginContext ctx = buildContext("t1", "LEAD", "task");
        PluginResult result = plugin.invoke(ctx);
        
        assertThat(result.status()).isEqualTo(PluginResult.PluginStatus.FAILURE);
        assertThat(result.evidence())
            .anyMatch(e -> e instanceof CommandExitEvidence cmd && cmd.exitCode() == 1);
    }
    
    @Test
    void shouldCancelProcess() throws Exception {
        MockProcess mockProcess = mockFactory.mockProcess("", 0, "");
        
        PluginContext ctx = buildContext("t1", "LEAD", "task");
        // 启动 invoke（不等待）
        Thread t = new Thread(() -> plugin.invoke(ctx));
        t.start();
        Thread.sleep(100);  // 让子进程启动
        
        plugin.cancel();
        
        assertThat(mockProcess.destroyed).isTrue();
    }
    
    @Test
    void shouldBuildPromptWithContext() {
        AgentTask task = new AgentTask(
            "t1", "REVIEWER", "审查 auth 模块",
            List.of("不能破坏现有 API"),
            new TaskContext(
                "Project: Auth Service\nTech: Node.js, JWT",
                List.of("architecture: microservices", "ADR-001: JWT over session"),
                List.of()
            ),
            List.of("safety"),
            null, null
        );
        
        PluginContext ctx = new PluginContext(
            task, null, "/tmp", 60_000, null
        );
        
        String prompt = invokeBuildPrompt(ctx);
        
        assertThat(prompt).contains("审查 auth 模块");
        assertThat(prompt).contains("[Constraints]");
        assertThat(prompt).contains("[Project Context]");
        assertThat(prompt).contains("safety");
    }
    
    @Test
    void shouldInspectHealth() {
        mockFactory.mockProcess("claude 2.1.215", 0, "");
        
        PluginHealth health = plugin.inspect();
        
        assertThat(health.status()).isEqualTo(PluginHealth.Status.HEALTHY);
    }
    
    @Test
    void shouldReportUnhealthyWhenCliMissing() {
        mockFactory.mockProcessFailure(new IOException("claude: command not found"));
        
        PluginHealth health = plugin.inspect();
        
        assertThat(health.status()).isEqualTo(PluginHealth.Status.UNHEALTHY);
    }
    
    // Helper
    
    private PluginContext buildContext(String taskId, String role, String objective) {
        AgentTask task = AgentTask.of(taskId, role, objective);
        return new PluginContext(task, null, System.getProperty("java.io.tmpdir"), 60_000, null);
    }
    
    private String invokeBuildPrompt(PluginContext ctx) {
        // 用反射访问私有 buildPrompt
        try {
            var method = ClaudeCodePlugin.class.getDeclaredMethod("buildPrompt", PluginContext.class);
            method.setAccessible(true);
            return (String) method.invoke(plugin, ctx);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

### 8.2 MockProcessFactory

```java
class MockProcessFactory implements ProcessFactory {
    
    String stdout;
    int exitCode;
    String stderr;
    IOException failure;
    
    public MockProcess mockProcess(String stdout, int exitCode, String stderr) {
        this.stdout = stdout;
        this.exitCode = exitCode;
        this.stderr = stderr;
        this.failure = null;
        return new MockProcess(this);
    }
    
    public void mockProcessFailure(IOException failure) {
        this.failure = failure;
    }
    
    @Override
    public Process start(ProcessBuilder pb) throws IOException {
        if (failure != null) throw failure;
        return new MockProcess(this);
    }
}

class MockProcess extends Process {
    final MockProcessFactory factory;
    volatile boolean destroyed;
    ByteArrayInputStream stdoutStream;
    ByteArrayInputStream stderrStream;
    PipedOutputStream stdin = new PipedOutputStream();
    
    MockProcess(MockProcessFactory factory) {
        this.factory = factory;
        this.stdoutStream = new ByteArrayInputStream(
            factory.stdout.getBytes(StandardCharsets.UTF_8)
        );
        this.stderrStream = new ByteArrayInputStream(
            factory.stderr.getBytes(StandardCharsets.UTF_8)
        );
    }
    
    @Override public OutputStream getOutputStream() { return stdin; }
    @Override public InputStream getInputStream() { return stdoutStream; }
    @Override public InputStream getErrorStream() { return stderrStream; }
    @Override public int waitFor() { return factory.exitCode; }
    @Override public boolean waitFor(long timeout, java.util.concurrent.TimeUnit unit) { return true; }
    @Override public int exitValue() { return factory.exitCode; }
    @Override public void destroy() { destroyed = true; }
    @Override public Process destroyForcibly() { destroyed = true; return this; }
    @Override public boolean isAlive() { return !destroyed; }
}
```

---

## 9. 手动验证

### 9.1 准备

```cmd
:: 1. 确保 Claude Code CLI 已安装
claude --version

:: 2. 设置 API Key（如果没有）
set ANTHROPIC_API_KEY=sk-...

:: 3. 启动 TeamMind
cd backend
mvn -B spring-boot:run
```

### 9.2 测试 Plugin 加载

```cmd
:: 调用 plugin inspect API（需实现 /api/plugins/health）
curl http://localhost:8080/api/plugins/claude-code/health
```

期望：

```json
{
  "pluginId": "claude-code",
  "status": "HEALTHY",
  "message": null
}
```

### 9.3 测试任务执行

通过 Web UI 创建项目，提交任务：

> "在 /tmp/test-project 中创建 README.md，包含项目说明"

观察：
- WebSocket 实时收到 chunk
- TaskExecution 入库
- Evidence 包含 COMMAND_EXIT（exitCode=0）
- 文件确实被创建（用 Git Verifier 验证）

---

## 10. 验收清单

- [ ] ClaudeCodePlugin 完整实现
- [ ] Plugin Metadata 与 YAML 一致
- [ ] 单元测试覆盖率 ≥ 85%
- [ ] 所有测试通过
- [ ] Mock 测试能完整模拟 CLI 行为
- [ ] 真实环境下能调用 Claude Code
- [ ] Stream 实时输出可观察
- [ ] Cancel 可正常工作

---

## 11. 踩坑记录

> 实施中遇到的问题，更新在此。

---

## 12. 接下来

- 读 [w3-codex-plugin.md](w3-codex-plugin.md)，实现 Codex Plugin
- 或读 [w3-verifier-plugins.md](w3-verifier-plugins.md)，实现 Verifier

---

**最后更新**：2026-08-14
**版本**：v0.1 Draft
# W3.3: Verifier Plugins 实现

> 独立 Evidence Verifier —— 防止 Agent 自报闭环。
>
> 预计工作量：**1 天**

---

## 任务目标

实现核心 Verifier Plugin：

- `GitVerifier`：用 `git diff` 验证 Agent 报告的文件修改
- `TestRunnerVerifier`：运行测试验证代码确实可用

设计原则：**Verifier 永远不应该信任 Agent 的自报，必须独立验证**。

---

## DoD

- [ ] GitVerifier 能验证 CodeDiffArtifact 中的文件修改
- [ ] TestRunnerVerifier 能根据 project type 运行测试
- [ ] 验证失败时产生清晰的 Finding
- [ ] 单元测试覆盖各种场景（成功 / 失败 / 部分失败）

---

## 1. GitVerifier

### 1.1 接口

**位置**：`com.teammind.verifiers.GitVerifier`

```java
package com.teammind.verifiers;

import com.teammind.plugin.*;
import com.teammind.plugin.artifacts.CodeDiffArtifact;
import com.teammind.plugin.evidence.CommandExitEvidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class GitVerifier implements Plugin {
    
    private static final Logger log = LoggerFactory.getLogger(GitVerifier.class);
    
    @Override
    public PluginMetadata metadata() {
        return new PluginMetadata(
            "git-verifier",
            PluginMetadata.PluginType.VERIFIER,
            "Git Verifier",
            "1.0.0",
            "TeamMind",
            "用 git CLI 独立验证 Agent 报告的文件修改",
            null,
            AgentPhilosophy.empty(),
            Map.of()
        );
    }
    
    @Override
    public List<CapabilityDescriptor> capabilities() {
        return List.of(
            new CapabilityDescriptor("verify_git_diff", CapabilityDescriptor.CapabilityQuality.EXCELLENT, null)
        );
    }
    
    @Override
    public PluginLifecycle lifecycle() {
        return PluginLifecycle.empty();
    }
    
    @Override
    public PluginResult invoke(PluginContext context) {
        AgentTask task = context.task();
        List<Artifact> previousArtifacts = task.context() != null 
            ? task.context().previousArtifacts() 
            : List.of();
        
        List<Finding> findings = new ArrayList<>();
        List<Evidence> evidence = new ArrayList<>();
        boolean allVerified = true;
        
        // 1. 提取 CodeDiffArtifact
        List<CodeDiffArtifact> diffs = previousArtifacts.stream()
            .filter(a -> a instanceof CodeDiffArtifact)
            .map(a -> (CodeDiffArtifact) a)
            .toList();
        
        if (diffs.isEmpty()) {
            return PluginResult.success(
                metadata().id(), task.taskId(),
                "No code diff artifacts to verify",
                List.of(), List.of()
            );
        }
        
        // 2. 对每个 diff 验证
        for (CodeDiffArtifact diff : diffs) {
            VerificationResult result = verifyDiff(diff, context.workDir());
            evidence.add(result.evidence);
            
            if (!result.verified) {
                allVerified = false;
                findings.add(new Finding(
                    Finding.Severity.HIGH,
                    "Git Verifier",
                    "Missing files",
                    result.message
                ));
            }
        }
        
        String summary = allVerified
            ? String.format("Verified %d files in git diff", diffs.stream().mapToInt(d -> d.files().size()).sum())
            : String.format("Verification failed: %d issues", findings.size());
        
        return new PluginResult(
            metadata().id(),
            task.taskId(),
            allVerified ? PluginResult.PluginStatus.SUCCESS : PluginResult.PluginStatus.FAILURE,
            summary,
            List.of(),
            findings,
            List.of(),
            evidence,
            new SelfReport(allVerified ? 1.0 : 0.0, allVerified ? "EXCELLENT" : "POOR"),
            new PerformanceMetrics(java.time.Duration.ZERO, null, null),
            null
        );
    }
    
    private VerificationResult verifyDiff(CodeDiffArtifact diff, String workDir) {
        try {
            // 1. git status --short
            Process p = new ProcessBuilder("git", "status", "--short")
                .directory(new File(workDir))
                .redirectErrorStream(true)
                .start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = p.waitFor();
            String statusOutput = output.toString();
            
            Evidence evidence = new CommandExitEvidence(
                "git",
                List.of("status", "--short"),
                exitCode,
                statusOutput
            );
            
            // 2. 检查每个声明的文件是否在 git status 中
            Set<String> modifiedFiles = new HashSet<>();
            for (String line : statusOutput.split("\n")) {
                if (line.isBlank()) continue;
                // 格式: "XY filename" 或 "XY orig -> new"
                String filename = line.length() > 3 
                    ? line.substring(3).trim().split(" -> ")[1].trim()  // 处理重命名
                    : line.trim();
                modifiedFiles.add(filename);
            }
            
            List<String> missing = new ArrayList<>();
            for (var file : diff.files()) {
                if (!modifiedFiles.contains(file.path())) {
                    missing.add(file.path());
                }
            }
            
            if (missing.isEmpty()) {
                return new VerificationResult(true, "All files verified", evidence);
            } else {
                return new VerificationResult(false, 
                    "Files not in git status: " + String.join(", ", missing), evidence);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            Evidence evidence = new CommandExitEvidence(
                "git", List.of("status", "--short"), -1, e.getMessage()
            );
            return new VerificationResult(false, "Git command failed: " + e.getMessage(), evidence);
        }
    }
    
    private record VerificationResult(boolean verified, String message, Evidence evidence) {}
}
```

---

## 2. TestRunnerVerifier

### 2.1 接口

**位置**：`com.teammind.verifiers.TestRunnerVerifier`

```java
package com.teammind.verifiers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.plugin.*;
import com.teammind.plugin.artifacts.TestReportArtifact;
import com.teammind.plugin.evidence.CommandExitEvidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class TestRunnerVerifier implements Plugin {
    
    private static final Logger log = LoggerFactory.getLogger(TestRunnerVerifier.class);
    
    private final ObjectMapper json = new ObjectMapper();
    
    @Override
    public PluginMetadata metadata() {
        return new PluginMetadata(
            "test-runner-verifier",
            PluginMetadata.PluginType.VERIFIER,
            "Test Runner Verifier",
            "1.0.0",
            "TeamMind",
            "运行项目测试，独立验证 Agent 代码修改",
            null,
            AgentPhilosophy.empty(),
            Map.of()
        );
    }
    
    @Override
    public List<CapabilityDescriptor> capabilities() {
        return List.of(
            new CapabilityDescriptor("verify_tests", CapabilityDescriptor.CapabilityQuality.EXCELLENT, null)
        );
    }
    
    @Override
    public PluginLifecycle lifecycle() {
        return PluginLifecycle.empty();
    }
    
    @Override
    public PluginResult invoke(PluginContext context) {
        String workDir = context.workDir();
        List<Finding> findings = new ArrayList<>();
        
        // 1. 检测项目类型
        TestCommand testCommand = detectTestCommand(workDir);
        
        if (testCommand == null) {
            return PluginResult.success(
                metadata().id(), context.task().taskId(),
                "No test framework detected; skipping",
                List.of(), List.of()
            );
        }
        
        // 2. 运行测试
        RunResult runResult = runTest(testCommand, workDir);
        
        // 3. 构造 TestReportArtifact
        TestReportArtifact report = parseTestReport(runResult, testCommand.framework);
        
        // 4. 失败检查
        if (runResult.exitCode != 0) {
            for (var failure : report.failures()) {
                findings.add(new Finding(
                    Finding.Severity.HIGH,
                    "Test Runner",
                    "Test failure: " + failure.testName(),
                    failure.error()
                ));
            }
        }
        
        boolean passed = runResult.exitCode == 0;
        String summary = passed
            ? String.format("Tests passed: %d/%d", report.passed(), report.total())
            : String.format("Tests failed: %d/%d", report.total() - report.passed(), report.total());
        
        return new PluginResult(
            metadata().id(),
            context.task().taskId(),
            passed ? PluginResult.PluginStatus.SUCCESS : PluginResult.PluginStatus.FAILURE,
            summary,
            List.of(report),
            findings,
            List.of(),
            List.of(new CommandExitEvidence(
                testCommand.command, testCommand.args, runResult.exitCode, runResult.stdoutSummary
            )),
            new SelfReport(passed ? 1.0 : 0.5, passed ? "EXCELLENT" : "POOR"),
            new PerformanceMetrics(java.time.Duration.ofMillis(runResult.durationMs), null, null),
            null
        );
    }
    
    private TestCommand detectTestCommand(String workDir) {
        File dir = new File(workDir);
        
        if (new File(dir, "package.json").exists()) {
            // Node.js / React / Vue
            return new TestCommand("npm", List.of("test", "--", "--json"), "jest");
        }
        if (new File(dir, "pom.xml").exists()) {
            return new TestCommand("mvn", List.of("test", "-q"), "junit");
        }
        if (new File(dir, "build.gradle").exists()) {
            return new TestCommand("./gradlew", List.of("test"), "junit");
        }
        if (new File(dir, "pytest.ini").exists() || 
            new File(dir, "pyproject.toml").exists() ||
            new File(dir, "setup.py").exists()) {
            return new TestCommand("pytest", List.of("--tb=short"), "pytest");
        }
        if (new File(dir, "go.mod").exists()) {
            return new TestCommand("go", List.of("test", "./..."), "go-test");
        }
        
        return null;
    }
    
    private RunResult runTest(TestCommand testCommand, String workDir) {
        long start = System.currentTimeMillis();
        
        try {
            Process p = new ProcessBuilder(testCommand.command())
                .command().addAll(testCommand.args())
                .directory(new File(workDir))
                .redirectErrorStream(true)
                .start();
            
            StringBuilder stdout = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdout.append(line).append("\n");
                }
            }
            
            int exitCode = p.waitFor();
            long durationMs = System.currentTimeMillis() - start;
            
            // stdout 可能很长，截断保存
            String fullOutput = stdout.toString();
            String summary = fullOutput.length() > 1000 
                ? fullOutput.substring(0, 1000) + "..." 
                : fullOutput;
            
            return new RunResult(exitCode, summary, fullOutput, durationMs);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return new RunResult(-1, "Test execution failed: " + e.getMessage(), "", 0);
        }
    }
    
    private TestReportArtifact parseTestReport(RunResult runResult, String framework) {
        // 简化实现：从 stdout 文本解析
        // 实际实现应根据 framework 做格式适配
        int total = 0, passed = 0;
        List<TestReportArtifact.TestFailure> failures = new ArrayList<>();
        
        String stdout = runResult.fullOutput;
        
        // 通用解析：查找 "X passed, Y failed" 等模式
        // 这里简化实现
        if (stdout.contains(" passed")) {
            try {
                String passedPart = stdout.replaceAll(".*?(\\d+)\\s+passed.*", "$1");
                passed = Integer.parseInt(passedPart);
            } catch (Exception ignored) {}
        }
        if (stdout.contains(" failed")) {
            try {
                String failedPart = stdout.replaceAll(".*?(\\d+)\\s+failed.*", "$1");
                int failed = Integer.parseInt(failedPart);
                total = passed + failed;
            } catch (Exception ignored) {}
        }
        
        if (total == 0) {
            total = passed;
        }
        
        return new TestReportArtifact(framework, total, passed, total - passed, failures);
    }
    
    private record TestCommand(String command, List<String> args, String framework) {}
    private record RunResult(int exitCode, String stdoutSummary, String fullOutput, long durationMs) {}
}
```

---

## 3. TestReportArtifact

**位置**：`com.teammind.plugin.artifacts.TestReportArtifact`

```java
package com.teammind.plugin.artifacts;

import com.teammind.plugin.Artifact;

import java.util.List;
import java.util.Map;

public record TestReportArtifact(
    String framework,
    int total,
    int passed,
    int failed,
    List<TestFailure> failures
) implements Artifact {
    
    @Override
    public String type() { return "TEST_REPORT"; }
    
    @Override
    public String summary() {
        return String.format("%s: %d passed, %d failed", framework, passed, failed);
    }
    
    @Override
    public Map<String, Object> payload() {
        return Map.of(
            "framework", framework,
            "total", total,
            "passed", passed,
            "failed", failed,
            "failures", failures.stream().map(f -> Map.of(
                "testName", f.testName(),
                "error", f.error()
            )).toList()
        );
    }
    
    public record TestFailure(String testName, String error) {}
}
```

---

## 4. Finding 类型

**位置**：`com.teammind.plugin.Finding`

```java
package com.teammind.plugin;

public record Finding(
    Severity severity,
    String source,         // "Git Verifier" / "Codex" / ...
    String title,
    String description
) {
    public enum Severity { CRITICAL, HIGH, MEDIUM, LOW, INFO }
}
```

---

## 5. 单元测试

### 5.1 GitVerifierTest

```java
@ExtendWith(MockitoExtension.class)
class GitVerifierTest {
    
    GitVerifier verifier = new GitVerifier();
    
    @Test
    void shouldVerifyAllFilesPresent() throws Exception {
        // Mock git status
        String gitOutput = """
            M src/auth.ts
            M src/jwt.ts
            """;
        // ... 使用 mock process
        
        AgentTask task = new AgentTask(
            "t1", "VERIFIER", "verify",
            List.of(), 
            new TaskContext(null, null, List.of(
                new CodeDiffArtifact(
                    List.of(
                        new CodeDiffArtifact.CodeDiffFile("src/auth.ts", "MODIFY", "+jwt", 1, 0),
                        new CodeDiffArtifact.CodeDiffFile("src/jwt.ts", "MODIFY", "+token", 1, 0)
                    ),
                    2, 0
                )
            )),
            null, null, null
        );
        
        PluginContext ctx = new PluginContext(task, null, "/tmp", 60_000, null);
        
        // ... mock ProcessBuilder ...
        PluginResult result = verifier.invoke(ctx);
        
        assertThat(result.status()).isEqualTo(PluginResult.PluginStatus.SUCCESS);
    }
    
    @Test
    void shouldDetectMissingFiles() {
        // ... 类似上面，但 git status 缺少一个文件
        
        assertThat(result.status()).isEqualTo(PluginResult.PluginStatus.FAILURE);
        assertThat(result.findings()).hasSize(1);
        assertThat(result.findings().get(0).description()).contains("missing");
    }
    
    @Test
    void shouldHandleNoDiffsGracefully() {
        AgentTask task = new AgentTask(
            "t1", "VERIFIER", "verify",
            List.of(),
            new TaskContext(null, null, List.of()),  // 无 previousArtifacts
            null, null, null
        );
        PluginContext ctx = new PluginContext(task, null, "/tmp", 60_000, null);
        
        PluginResult result = verifier.invoke(ctx);
        
        assertThat(result.status()).isEqualTo(PluginResult.PluginStatus.SUCCESS);
    }
}
```

### 5.2 TestRunnerVerifierTest

```java
class TestRunnerVerifierTest {
    
    TestRunnerVerifier verifier = new TestRunnerVerifier();
    
    @Test
    void shouldDetectNpmProject() {
        // 创建临时 package.json
        File tmpDir = Files.createTempDirectory("test");
        Files.writeString(new File(tmpDir, "package.json").toPath(), "{}");
        
        // ... invoke ...
        
        assertThat(testCommand.framework).isEqualTo("jest");
    }
    
    @Test
    void shouldHandleNoTestFramework() {
        File tmpDir = Files.createTempDirectory("test");
        // 空目录
        
        PluginResult result = verifier.invoke(ctx);
        
        assertThat(result.summary()).contains("No test framework");
    }
    
    @Test
    void shouldReportFailureOnNonZeroExit() {
        // ... mock test exit code 1
        
        assertThat(result.status()).isEqualTo(PluginResult.PluginStatus.FAILURE);
        assertThat(result.findings()).isNotEmpty();
    }
}
```

---

## 6. Plugin Manifest 扩展

`backend/src/main/resources/adapters/verifiers.yaml`：

```yaml
verifiers:
  - id: git-verifier
    type: VERIFIER
    class: com.teammind.verifiers.GitVerifier
  - id: test-runner-verifier
    type: VERIFIER
    class: com.teammind.verifiers.TestRunnerVerifier
```

---

## 7. 验收清单

- [ ] GitVerifier 实现完整
- [ ] TestRunnerVerifier 实现完整
- [ ] 单测覆盖率 ≥ 85%
- [ ] 所有测试通过
- [ ] 真实环境下能验证 git diff
- [ ] 真实环境下能运行 npm test / pytest

---

## 8. 接下来

- 读 [w4-role-evolution.md](w4-role-evolution.md)，实现自适应闭环
- 或读 [testing-guide.md](testing-guide.md)，学习测试策略

---

**最后更新**：2026-08-14
**版本**：v0.1 Draft
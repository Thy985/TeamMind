package com.teammind.plugin.verifier;

import com.teammind.common.EventType;
import com.teammind.event.EventBus;
import com.teammind.event.TeamMindEvent;
import com.teammind.plugin.Plugin;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * TestRunnerVerifier — 验证测试是否通过
 *
 * 支持 Maven Surefire / Gradle Test / pytest 输出格式解析。
 * Evidence 类型：TEST_EXECUTION
 */
@Slf4j
public class TestRunnerVerifier implements Plugin {

    private static final String ID = "test-runner-verifier";
    private static final String VERSION = "1.0.0";
    private final EventBus eventBus;

    public TestRunnerVerifier(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    // ─── Plugin interface ──────────────────────────────────────

    @Override public String id() { return ID; }

    @Override public PluginType type() { return PluginType.VERIFIER; }

    @Override public String description() {
        return "执行测试命令并解析结果，验证测试是否全部通过";
    }

    @Override public String version() { return VERSION; }

    @Override
    public PluginMetadata metadata() {
        return new PluginMetadata(
                ID, "Test Runner Verifier", VERSION, description(),
                List.of("test_execution_verification", "build_verification"),
                List.of("deterministic", "fast", "low_cost"),
                List.of("verification", "tester"),
                List.of("implementation", "architecture_review"),
                15000L, 0.97, 0.0
        );
    }

    @Override
    public PluginResult invoke(PluginContext context) {
        String projectPath = context.projectPath() != null ? context.projectPath() : ".";
        String taskId = context.taskId();
        Map<String, Object> config = context.taskConfig();

        String testCommand = resolveTestCommand(projectPath, config);
        List<String> testArgs = resolveTestArgs(config);

        log.info("[{}] Running test verification: task={}, cmd={}, path={}",
                ID, taskId, testCommand, projectPath);

        eventBus.emit(TeamMindEvent.of(EventType.EVIDENCE_VERIFYING, taskId, ID, "TESTER",
                Map.of("test_command", testCommand, "evidence_type", "TEST_EXECUTION")));

        try {
            TestResult result = runTests(projectPath, testCommand, testArgs);

            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("evidence_type", "TEST_EXECUTION");
            evidence.put("task_id", taskId);
            evidence.put("command", testCommand + " " + String.join(" ", testArgs));
            evidence.put("exit_code", result.exitCode());
            evidence.put("tests_passed", result.testsPassed());
            evidence.put("tests_failed", result.testsFailed());
            evidence.put("tests_skipped", result.testsSkipped());
            evidence.put("total_tests", result.totalTests());
            evidence.put("passed", result.exitCode() == 0);
            evidence.put("output", "see log");

            EventType evidenceType = result.exitCode() == 0
                    ? EventType.EVIDENCE_VERIFIED
                    : EventType.EVIDENCE_FAILED;
            eventBus.emit(TeamMindEvent.of(evidenceType, taskId, ID, "TESTER", evidence));

            Map<String, Object> pluginResult = new HashMap<>();
            pluginResult.put("verified", result.exitCode() == 0);
            pluginResult.put("evidence", evidence);
            pluginResult.put("summary", String.format("%d passed, %d failed, %d skipped",
                    result.testsPassed(), result.testsFailed(), result.testsSkipped()));

            log.info("[{}] Test verification complete: {}", ID, pluginResult.get("summary"));
            return PluginResult.success(ID, pluginResult);

        } catch (Exception e) {
            log.error("[{}] Test verification failed: {}", ID, e.getMessage(), e);
            eventBus.emit(TeamMindEvent.of(EventType.EVIDENCE_FAILED, taskId, ID, "TESTER",
                    Map.of("error", e.getMessage())));
            return PluginResult.failure(ID, e.getMessage());
        }
    }

    @Override
    public PluginHealth inspect() {
        try {
            boolean maven = runCmd(".", "mvn", "--version");
            boolean gradle = runCmd(".", "gradle", "--version");
            boolean pytest = runCmd(".", "python", "-m", "pytest", "--version");
            return (maven || gradle || pytest) ? PluginHealth.HEALTHY : PluginHealth.DEGRADED;
        } catch (Exception e) {
            return PluginHealth.DEGRADED;
        }
    }

    @Override public void onLoad() { log.info("[{}] Test Runner Verifier loaded", ID); }
    @Override public void onUnload() {}

    // ─── Internal helpers ──────────────────────────────────────

    private String resolveTestCommand(String projectPath, Map<String, Object> config) {
        Object cmd = config.get("test_command");
        if (cmd instanceof String s && !s.isBlank()) return s.trim();

        Path base = Path.of(projectPath);
        if (Files.exists(base.resolve("pom.xml"))) return "mvn";
        if (Files.exists(base.resolve("build.gradle"))) return "gradle";
        if (Files.exists(base.resolve("package.json"))) return "npm";
        if (Files.exists(base.resolve("pytest.ini")) || Files.exists(base.resolve("pyproject.toml"))) return "pytest";
        return "mvn";
    }

    @SuppressWarnings("unchecked")
    private List<String> resolveTestArgs(Map<String, Object> config) {
        Object args = config.get("test_args");
        if (args instanceof List<?> list) return list.stream().map(Object::toString).toList();
        return List.of();
    }

    private TestResult runTests(String workdir, String command, List<String> args) throws Exception {
        List<String> fullCmd = new ArrayList<>();
        fullCmd.add(command);
        fullCmd.addAll(args);

        log.debug("[{}] Executing: {}", ID, fullCmd);

        ProcessBuilder pb = new ProcessBuilder(fullCmd);
        pb.directory(Path.of(workdir).toFile());
        pb.redirectErrorStream(true);

        Process proc = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }

        boolean completed = proc.waitFor(30, TimeUnit.MINUTES);
        int exitCode = completed ? proc.exitValue() : -1;

        ParsedResult parsed = parseOutput(output.toString(), exitCode);
        return new TestResult(exitCode, parsed.passed, parsed.failed, parsed.skipped, output.toString());
    }

    /**
     * 解析测试输出（Maven Surefire / Gradle / pytest 通用）
     */
    private ParsedResult parseOutput(String output, int exitCode) {
        int passed = 0, failed = 0, skipped = 0;

        // 匹配最后一行汇总： "Tests run: N, Failures: F, Errors: E, Skipped: S"
        for (String line : output.split("\n")) {
            if (line.contains("Tests run:") && line.contains("Failures:")) {
                try {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(line);
                    List<Integer> nums = new ArrayList<>();
                    while (m.find()) nums.add(Integer.parseInt(m.group(1)));
                    if (nums.size() >= 3) {
                        passed   = nums.get(0);
                        failed   = nums.get(1);
                        skipped  = nums.get(2);
                        break;
                    }
                } catch (Exception ignored) {}
            }
            if (line.contains("<failure ")) failed++;
            if (line.contains("<error "))    failed++;
            if (line.contains("<skipped ")) skipped++;
        }

        // fallback：退出码决定
        if (passed == 0 && failed == 0 && skipped == 0) {
            if (exitCode == 0) passed = 1;
            else failed = 1;
        }

        return new ParsedResult(passed, failed, skipped);
    }

    record TestResult(int exitCode, int testsPassed, int testsFailed, int testsSkipped, String output) {
        public int totalTests() { return testsPassed + testsFailed + testsSkipped; }
    }

    private record ParsedResult(int passed, int failed, int skipped) {}

    private boolean runCmd(String workdir, String... args) {
        try {
            Process p = new ProcessBuilder(args)
                    .directory(Path.of(workdir).toFile())
                    .redirectErrorStream(true)
                    .start();
            boolean done = p.waitFor(5, TimeUnit.SECONDS);
            return done && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "\n...[truncated]";
    }
}

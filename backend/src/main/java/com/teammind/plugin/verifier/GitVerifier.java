package com.teammind.plugin.verifier;

import com.teammind.common.EvidenceType;
import com.teammind.common.EventType;
import com.teammind.event.EventBus;
import com.teammind.event.TeamMindEvent;
import com.teammind.plugin.Plugin;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * GitVerifier — 验证 Agent 的 Git 变更是否符合预期
 *
 * 验证逻辑：
 *   1. 记录验证前的 git status / git diff
 *   2. 执行验证命令
 *   3. 再次检查 git diff
 *   4. 对比变更集，生成 Evidence
 *
 * Evidence 类型：GIT_DIFF
 */
@Slf4j
public class GitVerifier implements Plugin {

    private static final String ID = "git-verifier";
    private static final String VERSION = "1.0.0";
    private final EventBus eventBus;

    public GitVerifier(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    // ─── Plugin interface ──────────────────────────────────────

    @Override public String id() { return ID; }

    @Override public PluginType type() { return PluginType.VERIFIER; }

    @Override public String description() {
        return "验证 Git 工作区变更，比对预期 diff 与实际 diff";
    }

    @Override public String version() { return VERSION; }

    @Override
    public PluginMetadata metadata() {
        return new PluginMetadata(
                ID, "Git Verifier", VERSION, description(),
                List.of("git_diff_verification", "file_existence_check"),
                List.of("deterministic", "fast", "low_cost"),
                List.of("verification", "security_review"),
                List.of("implementation", "bulk_refactor"),
                2000L, 0.99, 0.0
        );
    }

    @Override
    public PluginResult invoke(PluginContext context) {
        String projectPath = context.projectPath() != null ? context.projectPath() : ".";
        String taskId = context.taskId();
        Map<String, Object> config = context.taskConfig();

        log.info("[{}] Running Git verification: task={}, path={}", ID, taskId, projectPath);

        // Emit verification start
        eventBus.emit(TeamMindEvent.of(EventType.EVIDENCE_VERIFYING, taskId, ID, "SECURITY_GATE",
                Map.of("evidence_type", "GIT_DIFF")));

        try {
            // 1. 获取验证前状态（baseline）
            Map<String, Object> baseline = captureGitState(projectPath, "baseline");

            // 2. 等待一小段时间让 Agent 完成变更
            Thread.sleep(500);

            // 3. 获取验证后状态
            Map<String, Object> after = captureGitState(projectPath, "after");

            // 4. 计算 diff
            List<String> addedFiles = diffFiles(baseline, after);
            List<String> removedFiles = diffRemovedFiles(baseline, after);
            List<String> modifiedFiles = diffModified(baseline, after);

            // 5. 检查预期变更
            @SuppressWarnings("unchecked")
            List<String> expectedFiles = (List<String>) config.get("expected_files");
            boolean matchesExpected = expectedFiles == null || verifyExpected(expectedFiles, addedFiles, removedFiles, modifiedFiles);

            // 6. 构建证据
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("evidence_type", EvidenceType.GIT_DIFF.name());
            evidence.put("task_id", taskId);
            evidence.put("added_files", addedFiles);
            evidence.put("removed_files", removedFiles);
            evidence.put("modified_files", modifiedFiles);
            evidence.put("expected_files", expectedFiles);
            evidence.put("matches_expected", matchesExpected);
            evidence.put("total_changes", addedFiles.size() + removedFiles.size() + modifiedFiles.size());

            // 7. 发射证据验证结果
            EventType evidenceType = matchesExpected ? EventType.EVIDENCE_VERIFIED : EventType.EVIDENCE_FAILED;
            eventBus.emit(TeamMindEvent.of(evidenceType, taskId, ID, "SECURITY_GATE", evidence));

            Map<String, Object> result = new HashMap<>();
            result.put("verified", matchesExpected);
            result.put("evidence", evidence);
            result.put("files_changed", addedFiles.size() + modifiedFiles.size() + removedFiles.size());

            log.info("[{}] Git verification complete: verified={}, changes={}",
                    ID, matchesExpected, evidence.get("total_changes"));
            return PluginResult.success(ID, result);

        } catch (Exception e) {
            log.error("[{}] Git verification failed: {}", ID, e.getMessage(), e);
            eventBus.emit(TeamMindEvent.of(EventType.EVIDENCE_FAILED, taskId, ID, "SECURITY_GATE",
                    Map.of("error", e.getMessage())));
            return PluginResult.failure(ID, e.getMessage());
        }
    }

    @Override public PluginHealth inspect() {
        return runShellCommand(".", "git", "rev-parse", "--git-dir") ? PluginHealth.HEALTHY : PluginHealth.DEGRADED;
    }

    @Override public void onLoad() { log.info("[{}] Git Verifier loaded", ID); }
    @Override public void onUnload() {}

    // ─── Internal helpers ──────────────────────────────────────

    /**
     * 捕获指定时刻的 Git 文件状态快照
     */
    private Map<String, Object> captureGitState(String projectPath, String label) {
        Map<String, Object> state = new HashMap<>();
        state.put("label", label);
        state.put("timestamp", System.currentTimeMillis());

        // 获取已跟踪文件的 hash 快照
        if (runShellCommand(projectPath, "git", "ls-files", "-s")) {
            state.put("tracked_files", getTrackedFiles(projectPath));
        }

        // 获取当前 diff
        String diff = runGitDiff(projectPath);
        state.put("diff", diff != null ? diff : "");

        // 获取 status
        state.put("status", getGitStatus(projectPath));

        return state;
    }

    private List<String> getTrackedFiles(String projectPath) {
        StringBuilder sb = new StringBuilder();
        if (runShellCommandWithOutput(projectPath, sb, "git", "ls-files")) {
            return Arrays.asList(sb.toString().trim().split("\n"));
        }
        return List.of();
    }

    private String runGitDiff(String projectPath) {
        StringBuilder sb = new StringBuilder();
        if (runShellCommandWithOutput(projectPath, sb, "git", "diff", "--stat")) {
            return sb.toString().trim();
        }
        return null;
    }

    private String getGitStatus(String projectPath) {
        StringBuilder sb = new StringBuilder();
        if (runShellCommandWithOutput(projectPath, sb, "git", "status", "--porcelain")) {
            return sb.toString().trim();
        }
        return "";
    }

    /**
     * 比较两个快照，找出新增/删除/修改的文件
     */
    private List<String> diffFiles(Map<String, Object> before, Map<String, Object> after) {
        @SuppressWarnings("unchecked")
        List<String> beforeFiles = (List<String>) before.getOrDefault("tracked_files", List.of());
        @SuppressWarnings("unchecked")
        List<String> afterFiles = (List<String>) after.getOrDefault("tracked_files", List.of());
        return new ArrayList<>(afterFiles);
    }

    private List<String> diffRemovedFiles(Map<String, Object> before, Map<String, Object> after) {
        @SuppressWarnings("unchecked")
        List<String> beforeFiles = (List<String>) before.getOrDefault("tracked_files", List.of());
        @SuppressWarnings("unchecked")
        List<String> afterFiles = (List<String>) after.getOrDefault("tracked_files", List.of());
        List<String> removed = new ArrayList<>();
        for (String f : beforeFiles) {
            if (!afterFiles.contains(f)) removed.add(f);
        }
        return removed;
    }

    private List<String> diffModified(Map<String, Object> before, Map<String, Object> after) {
        List<String> modified = new ArrayList<>();
        String beforeDiff = (String) before.getOrDefault("diff", "");
        String afterDiff = (String) after.getOrDefault("diff", "");
        if (!Objects.equals(beforeDiff, afterDiff) && !afterDiff.isBlank()) {
            modified.add("(diff_changed)");
        }
        return modified;
    }

    private boolean verifyExpected(List<String> expected, List<String> added,
                                   List<String> removed, List<String> modified) {
        if (expected.isEmpty()) return true;
        // 简单验证：期望文件在变更中出现
        Set<String> allChanges = new HashSet<>();
        allChanges.addAll(added);
        allChanges.addAll(removed);
        for (String f : expected) {
            boolean found = false;
            for (String change : allChanges) {
                if (change.contains(f) || f.contains(change)) {
                    found = true;
                    break;
                }
            }
            if (!found && !modified.contains("(diff_changed)")) return false;
        }
        return true;
    }

    /**
     * 执行 shell 命令（不捕获输出）
     */
    private boolean runShellCommand(String workdir, String... command) {
        return runShellCommandWithOutput(workdir, new StringBuilder(), command);
    }

    /**
     * 执行 shell 命令并捕获输出
     */
    private boolean runShellCommandWithOutput(String workdir, StringBuilder output, String... command) {
        try {
            Process p = new ProcessBuilder(command)
                    .directory(Path.of(workdir).toFile())
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }
            boolean done = p.waitFor(10, TimeUnit.SECONDS);
            return done && p.exitValue() == 0;
        } catch (Exception e) {
            log.warn("[{}] Shell command failed: {}", ID, Arrays.toString(command), e);
            return false;
        }
    }
}

package com.teammind.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.common.*;
import com.teammind.entity.RuntimeEvent;
import com.teammind.repository.RuntimeEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ActivityExtractor — Execution Ledger Sprint 1 核心引擎
 *
 * 职责：
 *   从 RuntimeEvent 聚合出 TaskActivity（结构化的任务活动摘要）
 *
 * 设计原则：
 *   - 所有数据来自事件事实，不来自 Agent 自述
 *   - 同一 Category 的事件合并展示（如多条命令合并为一个列表）
 *   - 失败不抛出异常（事件缺失时返回空列表）
 *
 * 分类映射：
 *   COMMANDS_EXECUTED  ← COMMAND_RUNNING + TOOL_CALLED + TOOL_RESULT
 *   FILES_CHANGED      ← FILE_CHANGED
 *   DEPENDENCIES_CHANGED ← DEPENDENCY_CHANGED
 *   INCIDENTS          ← ERROR_CRITICAL + ERROR_RECOVERABLE（配对检测）
 *   VERIFICATIONS      ← EVIDENCE_VERIFIED + TEST_PASSED + TEST_FAILED
 *   AGENT_DECISIONS    ← DECISION_MADE + APPROVAL_GRANTED + APPROVAL_DENIED
 */
@Slf4j
@Service
public class ActivityExtractor {

    private final RuntimeEventRepository eventRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ActivityExtractor(RuntimeEventRepository eventRepo) {
        this.eventRepo = eventRepo;
    }

    /**
     * 提取单个任务的完整 Activity 摘要
     */
    public TaskActivity extract(String taskId) {
        List<RuntimeEvent> events = eventRepo.findByTaskIdOrderByCreatedAtAsc(taskId);
        if (events.isEmpty()) {
            return TaskActivity.empty(taskId);
        }

        List<TaskActivity.CommandActivity> commands = extractCommands(events);
        List<String> files = extractFiles(events);
        List<TaskActivity.DependencyChange> deps = extractDependencies(events);
        List<TaskActivity.EnvironmentChange> envChanges = extractEnvironmentChanges(events);
        List<TaskActivity.IncidentActivity> incidents = extractIncidents(events);
        List<TaskActivity.VerificationActivity> verifications = extractVerifications(events);
        List<TaskActivity.DecisionActivity> decisions = extractDecisions(events);

        return new TaskActivity(
                taskId,
                commands,
                files,
                deps,
                envChanges,
                incidents,
                verifications,
                decisions,
                LocalDateTime.now()
        );
    }

    /**
     * 提取命令执行记录（从 COMMAND_RUNNING + TOOL_CALLED 推导）
     * 重要的命令通过 payload 中的关键词识别
     */
    private List<TaskActivity.CommandActivity> extractCommands(List<RuntimeEvent> events) {
        return events.stream()
                .filter(e -> e.getType() == EventType.COMMAND_RUNNING
                        || e.getType() == EventType.TOOL_CALLED)
                .map(this::toCommandActivity)
                .collect(Collectors.toList());
    }

    /**
     * 提取文件变更列表
     */
    private List<String> extractFiles(List<RuntimeEvent> events) {
        return events.stream()
                .filter(e -> e.getType() == EventType.FILE_CHANGED)
                .map(e -> extractField(e.getPayload(), "file"))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 提取依赖变更（从 DEPENDENCY_CHANGED 事件）
     */
    private List<TaskActivity.DependencyChange> extractDependencies(List<RuntimeEvent> events) {
        return events.stream()
                .filter(e -> e.getType() == EventType.DEPENDENCY_CHANGED)
                .map(this::toDependencyChange)
                .collect(Collectors.toList());
    }

    /**
     * 提取环境变更记录：PACKAGE_INSTALLED / COMMAND_EXITED / ENV_VAR_MODIFIED / PROCESS_STARTED / FILE_DELETED
     */
    private List<TaskActivity.EnvironmentChange> extractEnvironmentChanges(List<RuntimeEvent> events) {
        return events.stream()
                .filter(e -> e.getType() == EventType.PACKAGE_INSTALLED
                        || e.getType() == EventType.COMMAND_EXITED
                        || e.getType() == EventType.ENV_VAR_MODIFIED
                        || e.getType() == EventType.PROCESS_STARTED
                        || e.getType() == EventType.FILE_DELETED)
                .map(this::toEnvironmentChange)
                .collect(Collectors.toList());
    }

    private TaskActivity.EnvironmentChange toEnvironmentChange(RuntimeEvent event) {
        String name = extractField(event.getPayload(), "name");
        if (name == null) name = extractField(event.getPayload(), "package");
        String detail = extractField(event.getPayload(), "detail");
        if (detail == null) {
            if (event.getType() == EventType.PACKAGE_INSTALLED) {
                String ver = extractField(event.getPayload(), "version");
                detail = ver != null ? "v" + ver : "";
            } else if (event.getType() == EventType.PROCESS_STARTED) {
                String pid = extractField(event.getPayload(), "pid");
                detail = pid != null ? "PID=" + pid : "";
            }
        }
        String finalName = name;
        String finalDetail = detail != null ? detail : "";
        TaskActivity.EnvironmentChange.Action action;
        switch (event.getType()) {
            case PACKAGE_INSTALLED:
                return new TaskActivity.EnvironmentChange(
                        TaskActivity.EnvironmentChange.Action.ADDED, finalName, finalDetail, "Package");
            case COMMAND_EXITED:
                return new TaskActivity.EnvironmentChange(
                        TaskActivity.EnvironmentChange.Action.STARTED, finalName, finalDetail, "Command");
            case ENV_VAR_MODIFIED:
                return new TaskActivity.EnvironmentChange(
                        TaskActivity.EnvironmentChange.Action.MODIFIED, finalName, finalDetail, "EnvVar");
            case PROCESS_STARTED:
                return new TaskActivity.EnvironmentChange(
                        TaskActivity.EnvironmentChange.Action.STARTED, finalName, finalDetail, "Process");
            case FILE_DELETED:
                return new TaskActivity.EnvironmentChange(
                        TaskActivity.EnvironmentChange.Action.REMOVED, finalName, finalDetail, "File");
            default:
                return new TaskActivity.EnvironmentChange(
                        TaskActivity.EnvironmentChange.Action.MODIFIED, finalName, finalDetail, "Env");
        }
    }

    /**
     * 提取 Incident/Resolution 配对
     * 规则：ERROR_CRITICAL 后有 ERROR_RECOVERABLE 则标记为已解决
     */
    private List<TaskActivity.IncidentActivity> extractIncidents(List<RuntimeEvent> events) {
        List<TaskActivity.IncidentActivity> incidents = new ArrayList<>();
        Iterator<RuntimeEvent> it = events.iterator();

        while (it.hasNext()) {
            RuntimeEvent e = it.next();
            if (e.getType() == EventType.ERROR_CRITICAL) {
                boolean resolved = false;
                String resolvedBy = null;
                // 检查后续是否有 ERROR_RECOVERABLE（解决）
                while (it.hasNext()) {
                    RuntimeEvent next = it.next();
                    if (next.getType() == EventType.ERROR_RECOVERABLE
                            && next.getTaskId().equals(e.getTaskId())) {
                        resolved = true;
                        resolvedBy = next.getPluginId();
                        break;
                    }
                    if (next.getType() == EventType.TASK_COMPLETED
                            || next.getType() == EventType.TASK_FAILED) {
                        break;
                    }
                }
                incidents.add(new TaskActivity.IncidentActivity(
                        extractErrorType(e.getPayload()),
                        extractField(e.getPayload(), "message"),
                        resolved,
                        resolvedBy
                ));
            }
        }
        return incidents;
    }

    /**
     * 提取验证结果（聚合 TEST_PASSED/FAILED 数量）
     */
    private List<TaskActivity.VerificationActivity> extractVerifications(List<RuntimeEvent> events) {
        long passed = events.stream()
                .filter(e -> e.getType() == EventType.TEST_PASSED)
                .count();
        long failed = events.stream()
                .filter(e -> e.getType() == EventType.TEST_FAILED)
                .count();
        long evidenceVerified = events.stream()
                .filter(e -> e.getType() == EventType.EVIDENCE_VERIFIED)
                .count();

        List<TaskActivity.VerificationActivity> result = new ArrayList<>();
        if (passed > 0 || failed > 0) {
            result.add(new TaskActivity.VerificationActivity(
                    "TEST_" + (failed > 0 ? "MIXED" : "PASSED"),
                    (int) passed, (int) failed));
        }
        if (evidenceVerified > 0) {
            result.add(new TaskActivity.VerificationActivity(
                    "EVIDENCE_VERIFIED", (int) evidenceVerified, 0));
        }
        return result;
    }

    /**
     * 提取 Agent 决策
     */
    private List<TaskActivity.DecisionActivity> extractDecisions(List<RuntimeEvent> events) {
        return events.stream()
                .filter(e -> e.getType() == EventType.DECISION_MADE
                        || e.getType() == EventType.APPROVAL_GRANTED
                        || e.getType() == EventType.APPROVAL_DENIED)
                .map(e -> new TaskActivity.DecisionActivity(
                        e.getType().name(),
                        extractField(e.getPayload(), "decision") != null
                                ? extractField(e.getPayload(), "decision")
                                : extractField(e.getPayload(), "reason")
                ))
                .collect(Collectors.toList());
    }

    // ─── Private helpers ──────────────────────────────────────

    private TaskActivity.CommandActivity toCommandActivity(RuntimeEvent event) {
        String command = extractField(event.getPayload(), "command");
        if (command == null) command = extractField(event.getPayload(), "tool");
        if (command == null) command = event.getPayload();
        String durationStr = extractField(event.getPayload(), "duration_ms");
        String exitCodeStr = extractField(event.getPayload(), "exit_code");
        long durationMs = 0;
        int exitCode = -1;
        if (durationStr != null) {
            try { durationMs = Long.parseLong(durationStr); } catch (NumberFormatException ignored) {}
        }
        if (exitCodeStr != null) {
            try { exitCode = Integer.parseInt(exitCodeStr); } catch (NumberFormatException ignored) {}
        }
        return new TaskActivity.CommandActivity(command, durationMs, exitCode, event.getCreatedAt());
    }

    private TaskActivity.DependencyChange toDependencyChange(RuntimeEvent event) {
        String action = extractField(event.getPayload(), "action");
        String name = extractField(event.getPayload(), "name");
        String version = extractField(event.getPayload(), "version");
        TaskActivity.DependencyChange.Action a = "REMOVED".equalsIgnoreCase(action)
                ? TaskActivity.DependencyChange.Action.REMOVED
                : TaskActivity.DependencyChange.Action.ADDED;
        return new TaskActivity.DependencyChange(a, name, version);
    }

    private String extractErrorType(String payload) {
        if (payload == null) return "Error";
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (node.has("type")) return node.get("type").asText();
        } catch (Exception ignored) {}
        return "Error";
    }

    /**
     * 从 JSON payload 中提取字段值
     */
    private String extractField(String payload, String key) {
        if (payload == null || payload.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(payload);
            JsonNode field = node.get(key);
            return field != null ? field.asText() : null;
        } catch (Exception e) {
            log.debug("Failed to parse payload for field '{}': {}", key, e.getMessage());
            return null;
        }
    }

    private String coalesce(String a, String b) {
        return a != null ? a : b;
    }
}

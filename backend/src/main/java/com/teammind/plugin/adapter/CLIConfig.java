package com.teammind.plugin.adapter;

import com.teammind.common.DependencyType;
import com.teammind.common.PluginDependency;

import java.util.List;
import java.util.Map;

/**
 * CLIConfig — CLI 适配器声明式配置
 *
 * 从 YAML/JSON 加载，描述一个 CLI Agent 的所有行为特征。
 * 示例 codex.yaml:
 * ```yaml
 * cli_id: codex
 * command: "codex"
 * args: ["<prompt>"]
 * output_format: "text"
 * timeout_minutes: 60
 * health_check:
 *   command: "codex --version"
 *   expected_exit: 0
 * dependencies:
 *   - type: EXECUTABLE
 *     name: codex-cli
 *     check: "codex --version"
 * ```
 */
public record CLIConfig(
        String cliId,                  // 插件唯一 ID（如 "codex", "claude-code", "atomcode"）
        String command,                // CLI 可执行文件名（必须已在 PATH）
        List<String> args,             // 参数模板，<prompt> 会被替换为实际 prompt
        Map<String, String> env,       // 环境变量，支持 ${ENV:VAR} 占位符
        String workingDir,             // 工作目录（相对路径或绝对路径）
        int timeoutMinutes,            // 超时分钟数
        OutputFormat outputFormat,     // text | ndjson | structured
        HealthCheck healthCheck,       // 健康检查配置
        List<PluginDependency> dependencies  // 运行时依赖
) {
    public enum OutputFormat {
        TEXT,      // 普通文本输出（如 Codex）
        NDJSON,    // 每行一个 JSON（如 Claude Code --output-format json）
        STRUCTURED // 结构化 JSON 输出（如 Atomcode）
    }

    public record HealthCheck(
            String command,   // 健康检查命令
            int expectedExit  // 期望的退出码
    ) {
        public static final HealthCheck NONE = new HealthCheck(null, 0);
    }

    /**
     * 构建带默认值的 CLIConfig
     */
    public static CLIConfig of(String cliId, String command, OutputFormat format) {
        return new CLIConfig(
                cliId,
                command,
                List.of("<prompt>"),
                Map.of(),
                ".",
                60,
                format,
                HealthCheck.NONE,
                List.of()
        );
    }

    /**
     * 从 Map 构建（供 CLIDiscoveryService 使用）
     */
    @SuppressWarnings("unchecked")
    public static CLIConfig fromMap(Map<String, Object> map) {
        String cliId = (String) map.getOrDefault("cli_id", "unknown");
        String command = (String) map.getOrDefault("command", cliId);
        String outputFormat = (String) map.getOrDefault("output_format", "text");
        int timeout = map.get("timeout_minutes") instanceof Number n ? n.intValue() : 60;

        List<String> args = new java.util.ArrayList<>();
        Object argsObj = map.get("args");
        if (argsObj instanceof List<?> raw) {
            raw.forEach(a -> { if (a instanceof String s) args.add(s); });
        } else if (argsObj instanceof String s) {
            args.add(s);
        }

        Map<String, String> env = new java.util.HashMap<>();
        Object envObj = map.get("env");
        if (envObj instanceof Map<?, ?> rawEnv) {
            rawEnv.forEach((k, v) -> { if (k instanceof String ks && v instanceof String vs) env.put(ks, vs); });
        }

        String workDir = (String) map.getOrDefault("working_dir", ".");

        HealthCheck hc = HealthCheck.NONE;
        Object hcObj = map.get("health_check");
        if (hcObj instanceof Map<?, ?> hcMap) {
            String hcCmd = (String) hcMap.get("command");
            int hcExit = hcMap.get("expected_exit") instanceof Number n ? n.intValue() : 0;
            hc = new HealthCheck(hcCmd, hcExit);
        }

        List<PluginDependency> deps = List.of();
        Object depsObj = map.get("dependencies");
        if (depsObj instanceof List<?> rawDeps) {
            deps = rawDeps.stream()
                    .filter(d -> d instanceof Map)
                    .map(d -> parseDependency((Map<String, Object>) d))
                    .toList();
        }

        return new CLIConfig(
                cliId, command, args, env, workDir,
                timeout, OutputFormat.valueOf(outputFormat.toUpperCase()),
                hc, deps
        );
    }

    @SuppressWarnings("unchecked")
    private static PluginDependency parseDependency(Map<String, Object> map) {
        DependencyType type = DependencyType.valueOf(
                (String) ((String) map.getOrDefault("type", "EXECUTABLE")).toUpperCase());
        String name = (String) map.getOrDefault("name", "unknown");
        String checkCommand = (String) map.get("check_command");
        String endpoint = (String) map.get("endpoint");
        int timeoutMs = map.get("health_check_timeout_ms") instanceof Number n ? n.intValue() : 5000;

        return new PluginDependency(
                type, name, checkCommand, endpoint, null, null,
                timeoutMs == 5000 ? null : timeoutMs, null);
    }
}

package com.teammind.runtime;

import com.teammind.common.*;
import com.teammind.plugin.Plugin;
import com.teammind.plugin.PluginManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ReadinessManager — Agent Readiness 子系统核心
 *
 * 职责：
 *   1. 扫描所有 Plugin 的依赖
 *   2. 执行检查（CLI 版本、HTTP endpoint、认证文件等）
 *   3. 尝试自动恢复
 *   4. 返回 ReadinessResult 供 CapabilityRouter 前置过滤
 *
 * 设计原则：
 *   - 不硬编码任何 Plugin 特定的逻辑
 *   - 每个 Plugin 通过 dependencies() 声明自己需要什么
 *   - Recovery 策略也是声明式的（在 PluginDependency 里）
 */
@Slf4j
@Component
public class ReadinessManager {

    private final PluginManager pluginManager;
    private final Map<String, ReadinessResult> cache = new ConcurrentHashMap<>();
    private LocalDateTime lastScanTime;

    public ReadinessManager(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    /**
     * 检查单个 Plugin 的就绪状态。
     * 结果缓存在内存中，避免每次路由都重新探测。
     */
    public ReadinessResult check(String pluginId) {
        // 如果缓存有效期 < 30 秒，直接使用缓存
        if (lastScanTime != null
                && Duration.between(lastScanTime, LocalDateTime.now()).getSeconds() < 30
                && cache.containsKey(pluginId)) {
            return cache.get(pluginId);
        }

        Optional<Plugin> opt = pluginManager.findById(pluginId);
        if (opt.isEmpty()) {
            return ReadinessResult.unavailable(pluginId, "Plugin not registered", List.of("NOT_REGISTERED"));
        }

        Plugin plugin = opt.get();
        List<PluginDependency> deps = plugin.dependencies();

        if (deps.isEmpty()) {
            // 没有声明依赖 → 默认 READY（向后兼容旧 Plugin）
            var result = ReadinessResult.ready(pluginId);
            cache.put(pluginId, result);
            return result;
        }

        List<String> failedChecks = new ArrayList<>();
        Map<String, Object> details = new LinkedHashMap<>();

        for (PluginDependency dep : deps) {
            DependencyCheckResult check = checkDependency(pluginId, dep);
            details.put(dep.name(), check.details());
            if (!check.passed()) {
                failedChecks.add(dep.name() + ": " + check.reason());
            }
        }

        ReadinessResult result;
        if (failedChecks.isEmpty()) {
            result = ReadinessResult.ready(pluginId);
        } else if (isRecoverable(failedChecks, deps)) {
            result = ReadinessResult.builder()
                    .pluginId(pluginId)
                    .state(ReadinessState.DEGRADED)
                    .diagnosis("Some dependencies failed but can recover: " + String.join(", ", failedChecks))
                    .readinessScore(0.5)
                    .failedChecks(failedChecks)
                    .details(details)
                    .build();
        } else {
            result = ReadinessResult.unavailable(pluginId,
                    "Dependencies not met: " + String.join(", ", failedChecks), failedChecks);
        }

        cache.put(pluginId, result);
        return result;
    }

    /**
     * 批量检查所有 Plugin。
     */
    public Map<String, ReadinessResult> checkAll() {
        lastScanTime = LocalDateTime.now();
        cache.clear();

        Map<String, ReadinessResult> results = new LinkedHashMap<>();
        for (Plugin plugin : pluginManager.getAll()) {
            results.put(plugin.id(), check(plugin.id()));
        }
        return results;
    }

    /**
     * 尝试自动恢复不可用的 Plugin。
     * 按 Dependency Graph 顺序执行 recovery 策略。
     *
     * @return true 如果恢复成功
     */
    public boolean attemptRecovery(String pluginId) {
        ReadinessResult current = check(pluginId);
        if (current.state() == ReadinessState.READY) {
            return true; // 已经是健康的，无需恢复
        }

        Optional<Plugin> opt = pluginManager.findById(pluginId);
        if (opt.isEmpty()) return false;

        Plugin plugin = opt.get();
        List<PluginDependency> deps = plugin.dependencies();

        for (PluginDependency dep : deps) {
            if (!dep.hasAutoRecovery()) continue;

            DependencyCheckResult failedCheck = findFailedCheck(current, dep.name());
            if (failedCheck == null) continue; // 这个依赖当前是好的

            log.info("Attempting recovery for plugin '{}' dependency '{}': {}",
                    pluginId, dep.name(), failedCheck.reason());

            try {
                RecoveryAction action = classifyRecoveryAction(dep);
                if (action == RecoveryAction.DANGEROUS || action == RecoveryAction.IRREVERSIBLE) {
                    log.warn("Recovery requires human approval (action={}), skipping for '{}'",
                            action, pluginId);
                    continue;
                }

                boolean recovered = tryLaunchProcess(dep);
                if (recovered) {
                    log.info("Recovery succeeded for plugin '{}' dependency '{}'", pluginId, dep.name());
                    cache.remove(pluginId); // 清除缓存，下次 check() 会重新验证
                    return true;
                }
            } catch (Exception e) {
                log.warn("Recovery failed for plugin '{}' dependency '{}': {}",
                        pluginId, dep.name(), e.getMessage());
            }
        }

        // 所有恢复尝试都失败了
        cache.put(pluginId, ReadinessResult.blocked(pluginId,
                "Auto-recovery failed for: " + String.join(", ", current.failedChecks())));
        return false;
    }

    /**
     * 获取所有 READY 的 Plugin（Capability Router 前置过滤用）。
     */
    public List<Plugin> getRunnableAgents() {
        return pluginManager.getAllAgents().stream()
                .filter(p -> check(p.id()).isRunnable())
                .toList();
    }

    /**
     * 清除指定 Plugin 的缓存（恢复成功后调用）。
     */
    public void invalidateCache(String pluginId) {
        cache.remove(pluginId);
    }

    // ─── private helpers ──────────────────────────────────────

    private DependencyCheckResult checkDependency(String pluginId, PluginDependency dep) {
        switch (dep.type()) {
            case EXECUTABLE -> {
                return checkExecutable(pluginId, dep);
            }
            case SERVICE -> {
                return checkService(pluginId, dep);
            }
            case AUTH -> {
                return checkAuth(pluginId, dep);
            }
            case WORKSPACE -> {
                return checkWorkspace(pluginId, dep);
            }
            case ENVIRONMENT -> {
                return checkEnvironment(pluginId, dep);
            }
            case SYSTEM_LIBRARY -> {
                return checkSystemLibrary(pluginId, dep);
            }
            default -> {
                // 未知类型视为通过
                return DependencyCheckResult.passed(dep.name());
            }
        }
    }

    /**
     * 检查 CLI 可执行文件是否存在且版本满足要求。
     */
    private DependencyCheckResult checkExecutable(String pluginId, PluginDependency dep) {
        if (dep.checkCommand() == null || dep.checkCommand().isBlank()) {
            // 没有指定检查命令，跳过
            return DependencyCheckResult.passed(dep.name());
        }

        try {
            Process p = new ProcessBuilder(dep.checkCommand().split(" "))
                    .redirectErrorStream(true)
                    .start();
            boolean finished = p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            int exit = finished ? p.exitValue() : -1;

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("command", dep.checkCommand());
            details.put("exitCode", exit);

            if (exit != 0) {
                return DependencyCheckResult.failed(dep.name(),
                        "Command '" + dep.checkCommand() + "' exited with code " + exit, details);
            }

            // 检查最小版本
            if (dep.minVersion() != null) {
                String output = finished ? new String(p.getInputStream().readAllBytes()) : "";
                if (!isVersionAtLeast(output, dep.minVersion())) {
                    return DependencyCheckResult.failed(dep.name(),
                            "Version mismatch: need >= " + dep.minVersion(), details);
                }
            }

            details.put("status", "OK");
            return DependencyCheckResult.passed(dep.name(), details);
        } catch (Exception e) {
            return DependencyCheckResult.failed(dep.name(), e.getMessage(), Map.of());
        }
    }

    /**
     * 检查 HTTP 服务是否可达。
     */
    private DependencyCheckResult checkService(String pluginId, PluginDependency dep) {
        if (dep.endpoint() == null || dep.endpoint().isBlank()) {
            return DependencyCheckResult.passed(dep.name());
        }

        try {
            String healthPath = dep.endpoint();
            if (!healthPath.contains("://")) {
                healthPath = "http://" + healthPath;
            }
            // 添加 health check path
            if (!healthPath.endsWith("/v1/models") && !healthPath.endsWith("/health")) {
                healthPath = healthPath.replaceFirst("/v1$", "/v1/models");
            }

            URL url = new URL(healthPath);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(dep.healthCheckTimeoutMs() != null ? dep.healthCheckTimeoutMs() : 5000);
            conn.setReadTimeout(dep.healthCheckTimeoutMs() != null ? dep.healthCheckTimeoutMs() : 5000);

            int status = conn.getResponseCode();
            conn.disconnect();

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("endpoint", dep.endpoint());
            details.put("httpStatus", status);

            if (status != 200) {
                return DependencyCheckResult.failed(dep.name(),
                        "HTTP " + status + " from " + dep.endpoint(), details);
            }

            details.put("status", "OK");
            return DependencyCheckResult.passed(dep.name(), details);
        } catch (Exception e) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("endpoint", dep.endpoint());
            details.put("error", e.getMessage());
            return DependencyCheckResult.failed(dep.name(),
                    "Cannot reach " + dep.endpoint() + ": " + e.getMessage(), details);
        }
    }

    /**
     * 检查认证/配置文件是否存在。
     */
    private DependencyCheckResult checkAuth(String pluginId, PluginDependency dep) {
        if (dep.checkCommand() == null || dep.checkCommand().isBlank()) {
            return DependencyCheckResult.passed(dep.name());
        }
        // AUTH 类型使用 shell 命令检查
        return checkExecutable(pluginId, dep);
    }

    /**
     * 检查工作区是否是 Git 仓库。
     */
    private DependencyCheckResult checkWorkspace(String pluginId, PluginDependency dep) {
        if (dep.checkCommand() == null || dep.checkCommand().isBlank()) {
            return DependencyCheckResult.passed(dep.name());
        }
        return checkExecutable(pluginId, dep);
    }

    /**
     * 检查环境变量是否存在。
     */
    private DependencyCheckResult checkEnvironment(String pluginId, PluginDependency dep) {
        if (dep.checkCommand() == null || dep.checkCommand().isBlank()) {
            return DependencyCheckResult.passed(dep.name());
        }
        return checkExecutable(pluginId, dep);
    }

    /**
     * 检查系统库是否存在。
     */
    private DependencyCheckResult checkSystemLibrary(String pluginId, PluginDependency dep) {
        if (dep.checkCommand() == null || dep.checkCommand().isBlank()) {
            return DependencyCheckResult.passed(dep.name());
        }
        return checkExecutable(pluginId, dep);
    }

    /**
     * 判断失败项是否可自动恢复。
     */
    private boolean isRecoverable(List<String> failedChecks, List<PluginDependency> deps) {
        for (String failed : failedChecks) {
            for (PluginDependency dep : deps) {
                if (failed.startsWith(dep.name() + ":") && dep.hasAutoRecovery()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 对应当前失败项的依赖检查
     */
    private DependencyCheckResult findFailedCheck(ReadinessResult result, String depName) {
        for (String failed : result.failedChecks()) {
            if (failed.startsWith(depName + ":")) {
                return new DependencyCheckResult(depName, false, failed.split(": ", 2)[1], Map.of());
            }
        }
        return null;
    }

    /**
     * 分类恢复操作的安全级别。
     */
    private RecoveryAction classifyRecoveryAction(PluginDependency dep) {
        if (dep.recoveryProcess() == null) return RecoveryAction.SAFE;
        // 启动新进程属于 DANGEROUS
        return RecoveryAction.DANGEROUS;
    }

    /**
     * 尝试启动恢复进程。
     */
    private boolean tryLaunchProcess(PluginDependency dep) {
        if (dep.recoveryProcess() == null) return false;

        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(dep.recoveryProcess());
            if (dep.recoveryArgs() != null) {
                cmd.addAll(List.of(dep.recoveryArgs()));
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            // 后台启动，不阻塞
            pb.start();

            log.info("Launched recovery process: {}", cmd);
            return true;
        } catch (IOException e) {
            log.warn("Failed to launch recovery process: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 版本比较：output 中的版本号是否 >= minVersion。
     */
    private boolean isVersionAtLeast(String output, String minVersion) {
        // 简单实现：提取版本号并比较
        // 实际生产环境需要更健壮的版本解析
        try {
            String extracted = output.replaceAll("[^0-9.]", "").trim();
            if (extracted.isBlank()) return true; // 无法提取版本，保守通过
            String[] current = extracted.split("\\.");
            String[] required = minVersion.split("\\.");
            for (int i = 0; i < Math.min(current.length, required.length); i++) {
                int cur = Integer.parseInt(current[i]);
                int req = Integer.parseInt(required[i]);
                if (cur > req) return true;
                if (cur < req) return false;
            }
            return current.length >= required.length;
        } catch (Exception e) {
            return true; // 解析失败，保守通过
        }
    }

    /**
     * 依赖检查结果内部类。
     */
    private record DependencyCheckResult(
            String name,
            boolean passed,
            String reason,
            Map<String, Object> details
    ) {
        static DependencyCheckResult passed(String name) {
            return new DependencyCheckResult(name, true, "OK", Map.of());
        }

        static DependencyCheckResult passed(String name, Map<String, Object> details) {
            return new DependencyCheckResult(name, true, "OK", details);
        }

        static DependencyCheckResult failed(String name, String reason, Map<String, Object> details) {
            return new DependencyCheckResult(name, false, reason, details);
        }
    }
}

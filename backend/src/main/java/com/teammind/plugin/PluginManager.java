package com.teammind.plugin;

import com.teammind.common.EventType;
import com.teammind.event.EventBus;
import com.teammind.event.TeamMindEvent;
import com.teammind.runtime.ReadinessManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Plugin 管理器 — Cordis-like 插件生命周期管理
 *
 * 职责：
 * 1. 注册 / 注销 Plugin
 * 2. 管理 Plugin 生命周期（onLoad / onUnload）
 * 3. 按 ID / 类型 / 能力查询 Plugin
 * 4. 健康检查与自动降级
 */
@Slf4j
@Component
public class PluginManager {

    private final EventBus eventBus;
    private final ReadinessManager readinessManager;
    private final Map<String, Plugin> plugins = new ConcurrentHashMap<>();
    private final Map<String, Plugin> pluginsById = new ConcurrentHashMap<>();
    private final Map<Plugin.PluginType, List<Plugin>> pluginsByType = new ConcurrentHashMap<>();

    public PluginManager(EventBus eventBus, ReadinessManager readinessManager) {
        this.eventBus = eventBus;
        this.readinessManager = readinessManager;
    }

    /**
     * 注册 Plugin（含生命周期回调）
     */
    public void register(Plugin plugin) {
        if (pluginsById.containsKey(plugin.id())) {
            log.warn("Plugin '{}' already registered, replacing", plugin.id());
            unregister(plugin.id());
        }
        plugin.onLoad();
        pluginsById.put(plugin.id(), plugin);
        pluginsByType
                .computeIfAbsent(plugin.type(), k -> new ArrayList<>())
                .add(plugin);
        plugins.put(plugin.id(), plugin);
        log.info("Plugin registered: id={} type={} version={}",
                plugin.id(), plugin.type(), plugin.version());

        eventBus.emit(TeamMindEvent.of(
                EventType.AGENT_STARTED, "system", plugin.id(), "system"));
    }

    /**
     * 注销 Plugin
     */
    public void unregister(String pluginId) {
        Plugin plugin = pluginsById.remove(pluginId);
        if (plugin == null) return;

        plugin.onUnload();
        plugins.remove(pluginId);
        pluginsByType.values().forEach(list -> list.remove(plugin));
        pluginsByType.entrySet().removeIf(e -> e.getValue().isEmpty());

        log.info("Plugin unregistered: id={}", pluginId);
    }

    /**
     * 按 ID 查找
     */
    public Optional<Plugin> findById(String pluginId) {
        return Optional.ofNullable(pluginsById.get(pluginId));
    }

    /**
     * 按类型查找全部
     */
    public List<Plugin> findByType(Plugin.PluginType type) {
        return Collections.unmodifiableList(
                pluginsByType.getOrDefault(type, List.of()));
    }

    /**
     * 按能力查找（返回所有声明了该能力的 Plugin）
     */
    public List<Plugin> findByCapability(String capability) {
        return pluginsById.values().stream()
                .filter(p -> p.metadata().capabilities().contains(capability))
                .toList();
    }

    /**
     * 按能力 + 质量过滤
     */
    public List<Plugin> findByCapabilityAndQuality(String capability, String quality) {
        return pluginsById.values().stream()
                .filter(p -> p.metadata().capabilities().contains(capability))
                .filter(p -> matchesQuality(p, quality))
                .toList();
    }

    /**
     * 获取所有已注册 Plugin
     */
    public Collection<Plugin> getAll() {
        return Collections.unmodifiableCollection(pluginsById.values());
    }

    /**
     * 获取所有 Agent 类型 Plugin
     */
    public List<Plugin> getAllAgents() {
        return findByType(Plugin.PluginType.AGENT);
    }

    /**
     * 健康检查 — 批量检测
     */
    public Map<String, Plugin.PluginHealth> checkAllHealth() {
        Map<String, Plugin.PluginHealth> results = new LinkedHashMap<>();
        for (Plugin plugin : pluginsById.values()) {
            try {
                results.put(plugin.id(), plugin.inspect());
            } catch (Exception e) {
                log.warn("Health check failed for plugin '{}': {}", plugin.id(), e.getMessage());
                results.put(plugin.id(), Plugin.PluginHealth.DOWN);
            }
        }
        return results;
    }

    /**
     * 获取可用 Plugin（Readiness 为 READY 或 DEGRADED）
     */
    public List<Plugin> getAvailable() {
        return pluginsById.values().stream()
                .filter(p -> {
                    if (readinessManager == null) {
                        try { return p.inspect() != Plugin.PluginHealth.DOWN; }
                        catch (Exception e) { return false; }
                    }
                    return readinessManager.check(p.id()).isRunnable();
                })
                .toList();
    }

    /**
     * 获取所有 READY 的 Agent Plugin（严格过滤，DEGRADED 不包含）
     */
    public List<Plugin> getReadyAgents() {
        return pluginsById.values().stream()
                .filter(p -> p.type() == Plugin.PluginType.AGENT)
                .filter(p -> {
                    if (readinessManager == null) {
                        try { return p.inspect() == Plugin.PluginHealth.HEALTHY; }
                        catch (Exception e) { return false; }
                    }
                    return readinessManager.check(p.id()).state() == com.teammind.common.ReadinessState.READY;
                })
                .toList();
    }

    /**
     * 批量加载 — 从配置创建 Plugin 实例
     */
    public void loadFromConfig(Map<String, Object> config) {
        List<Map<String, Object>> plugins = (List<Map<String, Object>>) config.get("plugins");
        if (plugins == null) return;

        for (Map<String, Object> pluginConfig : plugins) {
            String id = (String) pluginConfig.get("id");
            if (id == null) continue;

            Plugin plugin = createPluginFromConfig(id, pluginConfig);
            if (plugin != null) {
                register(plugin);
            }
        }
    }

    /**
     * 根据配置创建 Plugin 实例
     * 子类可以重写此方法扩展 Plugin 工厂逻辑
     */
    protected Plugin createPluginFromConfig(String id, Map<String, Object> config) {
        log.debug("Creating plugin from config: id={}", id);
        return null; // 默认返回 null（需要具体实现）
    }

    private boolean matchesQuality(Plugin plugin, String quality) {
        // 简单实现：从 metadata 中判断
        Double reliability = plugin.metadata().reliabilityScore();
        if (reliability == null) return false;
        return switch (quality.toUpperCase()) {
            case "EXCELLENT" -> reliability >= 0.9;
            case "GOOD"      -> reliability >= 0.75;
            case "FAIR"      -> reliability >= 0.5;
            default          -> true;
        };
    }
}

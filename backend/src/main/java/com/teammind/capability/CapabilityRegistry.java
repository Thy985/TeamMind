package com.teammind.capability;

import com.teammind.plugin.Plugin;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 能力注册表 — 按能力 ID 索引所有 Plugin
 *
 * 支持操作：
 * - findByCapability(String) → List<Plugin>
 * - findBestByCapability(String) → Optional<Plugin>
 * - register(Capability) → void
 */
@Component
public class CapabilityRegistry {

    /** capabilityId → List<Plugin> */
    private final Map<String, List<Plugin>> registry = new ConcurrentHashMap<>();

    /**
     * 注册 Plugin 的能力
     */
    public void register(Plugin plugin) {
        for (String capability : plugin.metadata().capabilities()) {
            registry.computeIfAbsent(capability, k -> new ArrayList<>()).add(plugin);
        }
    }

    /**
     * 注销 Plugin 的能力
     */
    public void unregister(Plugin plugin) {
        for (String capability : plugin.metadata().capabilities()) {
            List<Plugin> list = registry.get(capability);
            if (list != null) {
                list.remove(plugin);
                if (list.isEmpty()) {
                    registry.remove(capability);
                }
            }
        }
    }

    /**
     * 按能力查找
     */
    public List<Plugin> findByCapability(String capability) {
        return Collections.unmodifiableList(registry.getOrDefault(capability, List.of()));
    }

    /**
     * 按能力 + 质量过滤
     */
    public List<Plugin> findByCapabilityAndQuality(String capability, Capability.Quality minQuality) {
        return findByCapability(capability).stream()
                .filter(p -> isAtLeastQuality(p, minQuality))
                .toList();
    }

    /**
     * 按能力查找最佳 Plugin（可靠性最高）
     */
    public Optional<Plugin> findBestByCapability(String capability) {
        return findByCapability(capability).stream()
                .max(Comparator.comparingDouble(p ->
                        p.metadata().reliabilityScore() != null ? p.metadata().reliabilityScore() : 0.0));
    }

    /**
     * 检查是否注册了某能力
     */
    public boolean hasCapability(String capability) {
        return registry.containsKey(capability);
    }

    /**
     * 获取所有已注册能力
     */
    public Set<String> allCapabilities() {
        return registry.keySet();
    }

    private boolean isAtLeastQuality(Plugin plugin, Capability.Quality minQuality) {
        double reliability = plugin.metadata().reliabilityScore() != null
                ? plugin.metadata().reliabilityScore() : 0.0;
        return switch (minQuality) {
            case EXCELLENT -> reliability >= 0.9;
            case GOOD      -> reliability >= 0.75;
            case FAIR      -> reliability >= 0.5;
            case POOR      -> true;
        };
    }
}

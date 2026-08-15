package com.teammind.runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teammind.plugin.PluginManager;
import com.teammind.plugin.PluginRegistry;
import com.teammind.repository.PluginRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Map;

/**
 * Plugin 启动加载器 — 从数据库读取 Plugin 配置并注册到 PluginManager
 */
@Slf4j
@Component
public class PluginBootstrap implements ApplicationRunner {

    private final PluginRepository pluginRepository;
    private final PluginManager pluginManager;
    private final ObjectMapper objectMapper;
    private final PluginRegistry pluginRegistry;

    @Value("${teammind.plugins.use-database:true}")
    private boolean useDatabase;

    public PluginBootstrap(PluginRepository pluginRepository,
                             PluginManager pluginManager,
                             ObjectMapper objectMapper,
                             PluginRegistry pluginRegistry) {
        this.pluginRepository = pluginRepository;
        this.pluginManager = pluginManager;
        this.objectMapper = objectMapper;
        this.pluginRegistry = pluginRegistry;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!useDatabase || pluginManager.getAll().isEmpty()) {
            // 如果没有 DB 数据或配置为不使用 DB，使用内置注册表
            pluginRegistry.registerAll();
            log.info("Plugin bootstrap: loaded {} built-in plugins from registry",
                    pluginManager.getAll().size());
            return;
        }

        List<com.teammind.entity.Plugin> plugins = pluginRepository.findByEnabledTrue();
        log.info("Loading {} enabled plugins from database", plugins.size());

        for (com.teammind.entity.Plugin plugin : plugins) {
            try {
                PluginAdapter adapter = new PluginAdapter(plugin, objectMapper);
                pluginManager.register(adapter);
                log.info("Plugin loaded: id={} type={}", plugin.getId(), plugin.getPluginType());
            } catch (Exception e) {
                log.error("Failed to load plugin '{}': {}", plugin.getId(), e.getMessage(), e);
            }
        }

        log.info("Plugin bootstrap complete. Registered plugins: {}",
                pluginManager.getAll().size());
    }

    /**
     * JPA Entity → Plugin 接口适配器
     */
    static class PluginAdapter implements com.teammind.plugin.Plugin {
        private final com.teammind.entity.Plugin entity;
        private final ObjectMapper objectMapper;

        PluginAdapter(com.teammind.entity.Plugin entity, ObjectMapper objectMapper) {
            this.entity = entity;
            this.objectMapper = objectMapper;
        }

        @Override
        public String id() { return entity.getId(); }

        @Override
        public PluginType type() {
            return switch (entity.getPluginType()) {
                case TOOL -> PluginType.TOOL;
                case VERIFIER -> PluginType.VERIFIER;
                case MEMORY -> PluginType.MEMORY;
                case INTEGRATION -> PluginType.INTEGRATION;
                default -> PluginType.AGENT;
            };
        }

        @Override
        public String description() { return entity.getDescription() != null ? entity.getDescription() : ""; }

        @Override
        public String version() { return entity.getVersion() != null ? entity.getVersion() : "0.0.1"; }

        @Override
        public PluginMetadata metadata() {
            return new PluginMetadata(
                    entity.getId(),
                    entity.getName(),
                    version(),
                    description(),
                    entity.getCapabilities() != null ? entity.getCapabilities() : List.of(),
                    entity.getPhilosophies() != null ? entity.getPhilosophies() : List.of(),
                    entity.getPreferredRoles() != null ? entity.getPreferredRoles() : List.of(),
                    entity.getWeakRoles() != null ? entity.getWeakRoles() : List.of(),
                    entity.getAvgLatencyMs(),
                    entity.getReliabilityScore(),
                    entity.getCostPerInvocation()
            );
        }

        @Override
        public PluginResult invoke(PluginContext context) {
            return PluginResult.success(entity.getId(), Map.of(
                    "plugin_id", entity.getId(),
                    "note", "Invoke requires actual CLI integration"
            ));
        }

        @Override
        public PluginHealth inspect() {
            return switch (entity.getHealthStatus()) {
                case HEALTHY -> PluginHealth.HEALTHY;
                case DEGRADED -> PluginHealth.DEGRADED;
                case UNHEALTHY -> PluginHealth.UNHEALTHY;
                case DOWN -> PluginHealth.DOWN;
                default -> PluginHealth.HEALTHY;
            };
        }

        @Override
        public void onLoad() {}

        @Override
        public void onUnload() {}

        private List<String> parseJson(String json) {
            if (json == null || json.isBlank()) return List.of();
            try {
                return objectMapper.readValue(json, new TypeReference<>() {});
            } catch (Exception e) {
                return List.of();
            }
        }
    }
}

package com.teammind.plugin;

import com.teammind.event.EventBus;
import com.teammind.plugin.adapter.CLIConfig;
import com.teammind.plugin.adapter.GenericCLIPlugin;
import com.teammind.plugin.agent.ClaudeCodePlugin;
import com.teammind.plugin.agent.CodexPlugin;
import com.teammind.plugin.verifier.GitVerifier;
import com.teammind.plugin.verifier.TestRunnerVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Plugin Registry — 注册所有真实插件实现
 *
 * 替代 PluginBootstrap 的数据库加载，为测试和开发环境提供确定性注册。
 * 生产环境仍通过 PluginBootstrap 从 DB 加载。
 */
@Slf4j
@Component
public class PluginRegistry {

    private final EventBus eventBus;
    private final PluginManager pluginManager;

    public PluginRegistry(EventBus eventBus, PluginManager pluginManager) {
        this.eventBus = eventBus;
        this.pluginManager = pluginManager;
    }

    /**
     * 注册所有内置插件
     */
    public void registerAll() {
        register(new ClaudeCodePlugin(eventBus));
        register(new CodexPlugin(eventBus));
        register(new GitVerifier(eventBus));
        register(new TestRunnerVerifier(eventBus));
        log.info("PluginRegistry: {} built-in plugins registered", pluginManager.getAll().size());
        registerYAMLAdapters();
    }

    private void register(Plugin plugin) {
        pluginManager.register(plugin);
        plugin.onLoad();
        log.info("Registered plugin: id={} type={}", plugin.id(), plugin.type());
    }

    /**
     * 获取所有已注册插件 ID
     */
    public List<String> registeredIds() {
        return pluginManager.getAll().stream()
                .map(Plugin::id)
                .toList();
    }

    /**
     * 从 resources/cli-adapters/*.yaml 加载动态 CLI 适配器（Phase 3B）
     */
    private void registerYAMLAdapters() {
        int loaded = 0;
        try {
            ClassPathResource dir = new ClassPathResource("cli-adapters");
            if (dir.getFile().isDirectory()) {
                java.io.File[] files = dir.getFile().listFiles((d, name) -> name.endsWith(".yaml") || name.endsWith(".yml"));
                if (files != null) {
                    for (java.io.File f : files) {
                        try {
                            loadYAMLAdapter(f.toPath());
                            loaded++;
                        } catch (Exception e) {
                            log.warn("Failed to load YAML adapter {}: {}", f.getName(), e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("CLI adapters directory not found: {}", e.getMessage());
        }
        // Fallback: try filesystem
        if (loaded == 0) {
            Path diskDir = Path.of("backend/src/main/resources/cli-adapters");
            if (Files.exists(diskDir)) {
                try {
                    java.io.File[] diskFiles = diskDir.toFile().listFiles((d, name) -> name.endsWith(".yaml") || name.endsWith(".yml"));
                    if (diskFiles != null) {
                        for (java.io.File f : diskFiles) {
                            try {
                                loadYAMLAdapter(f.toPath());
                                loaded++;
                            } catch (Exception e) {
                                log.warn("Failed to load YAML adapter {}: {}", f.getName(), e.getMessage());
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        if (loaded > 0) {
            log.info("PluginRegistry: loaded {} YAML CLI adapter(s)", loaded);
        }
    }

    private void loadYAMLAdapter(Path yamlPath) throws Exception {
        try (InputStream is = Files.newInputStream(yamlPath)) {
            Yaml yaml = new Yaml();
            Map<String, Object> map = yaml.load(is);
            if (map == null) return;
            CLIConfig config = CLIConfig.fromMap(map);

            // Skip if a built-in plugin with the same ID already has rich capabilities.
            // Built-in plugins (ClaudeCodePlugin, CodexPlugin) have real metadata;
            // GenericCLIPlugin from YAML has empty capabilities and would lose that info.
            if (pluginManager.findById(config.cliId()).isPresent()) {
                Plugin existing = pluginManager.findById(config.cliId()).get();
                if (!existing.metadata().capabilities().isEmpty()) {
                    log.info("Skipping YAML adapter {} — built-in plugin already registered with capabilities: {}",
                            config.cliId(), existing.metadata().capabilities());
                    return;
                }
            }

            GenericCLIPlugin plugin = new GenericCLIPlugin(config, eventBus);
            pluginManager.register(plugin);
            log.info("Registered YAML adapter: {} (command={}, format={})",
                    config.cliId(), config.command(), config.outputFormat());
        }
    }
}

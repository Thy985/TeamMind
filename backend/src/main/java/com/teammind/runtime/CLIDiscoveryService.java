package com.teammind.runtime;

import com.teammind.event.EventBus;
import com.teammind.plugin.PluginManager;
import com.teammind.plugin.adapter.CLIAdapter;
import com.teammind.plugin.adapter.CLIConfig;
import com.teammind.plugin.adapter.GenericCLIPlugin;
import com.teammind.plugin.adapter.CLIProcessTracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * CLIDiscoveryService — 动态发现并注册 CLI 适配器
 *
 * 启动时扫描 resources/cli-adapters/*.yaml，
 * 每个 YAML → 创建 GenericCLIPlugin → 注册到 PluginManager。
 *
 * 无需修改 Java 代码即可添加新 CLI：
 *   1. 写一个 YAML 文件到 resources/cli-adapters/
 *   2. 重启服务
 */
@Slf4j
@Order(2)
@Component
public class CLIDiscoveryService implements CommandLineRunner, RuntimeLifecycle {

    private final PluginManager pluginManager;
    private final EventBus eventBus;
    private final CLIProcessTracker processTracker;
    private static final String ADAPTERS_DIR = "cli-adapters";

    public CLIDiscoveryService(PluginManager pluginManager,
                                EventBus eventBus,
                                CLIProcessTracker processTracker) {
        this.pluginManager = pluginManager;
        this.eventBus = eventBus;
        this.processTracker = processTracker;
    }

    @Override
    public void run(String... args) {
        // initialize() 由 RuntimeBootstrap @PostConstruct 调用，此处不再重复
    }

    @Override
    public void initialize() throws Exception {
        log.info("CLIDiscoveryService: scanning for CLI adapters in classpath:{}", ADAPTERS_DIR);

        int loaded = 0;
        try {
            // 尝试从 classpath 加载（通过 ClassLoader，不依赖 Spring）
            URL dirUrl = getClass().getClassLoader().getResource(ADAPTERS_DIR);
            if (dirUrl != null && "file".equals(dirUrl.getProtocol())) {
                java.io.File dirFile = new java.io.File(dirUrl.toURI());
                if (dirFile.isDirectory()) {
                    java.io.File[] files = dirFile.listFiles((d, name) -> name.endsWith(".yaml") || name.endsWith(".yml"));
                    if (files != null) {
                        for (java.io.File f : files) {
                            try {
                                loadAdapter(f.toPath());
                                loaded++;
                            } catch (Exception e) {
                                log.error("Failed to load adapter {}: {}", f.getName(), e.getMessage());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("CLI adapters directory not found in classpath: {}", e.getMessage());
        }

        // 如果 classpath 没找到，尝试从文件系统加载（开发环境）
        if (loaded == 0) {
            Path diskDir = Path.of("backend/src/main/resources", ADAPTERS_DIR);
            if (Files.exists(diskDir)) {
                java.io.File[] diskFiles = diskDir.toFile().listFiles((dir, name) -> name.endsWith(".yaml") || name.endsWith(".yml"));
                if (diskFiles != null) {
                    for (java.io.File diskF : diskFiles) {
                        try {
                            loadAdapter(diskF.toPath());
                            loaded++;
                        } catch (Exception e) {
                            log.error("Failed to load adapter {}: {}", diskF.getName(), e.getMessage());
                        }
                    }
                }
            }
        }

        log.info("CLIDiscoveryService: loaded {} CLI adapter(s)", loaded);
    }

    private void loadAdapter(Path yamlPath) {
        try (InputStream is = Files.newInputStream(yamlPath)) {
            Yaml yaml = new Yaml();
            Map<String, Object> map = yaml.load(is);
            if (map == null) {
                log.warn("Empty YAML file: {}", yamlPath.getFileName());
                return;
            }

            CLIConfig config = CLIConfig.fromMap(map);

            // Don't override built-in plugins that already have rich metadata/capabilities.
            // e.g. ClaudeCodePlugin has real capabilities; GenericCLIPlugin from YAML has empty caps.
            if (pluginManager.findById(config.cliId()).isPresent()) {
                com.teammind.plugin.Plugin existing = pluginManager.findById(config.cliId()).get();
                if (!existing.metadata().capabilities().isEmpty()) {
                    log.info("Skipping YAML adapter {} — built-in plugin already registered with capabilities: {}",
                            config.cliId(), existing.metadata().capabilities());
                    return;
                }
            }

            GenericCLIPlugin plugin = new GenericCLIPlugin(config, eventBus);
            pluginManager.register(plugin);

            log.info("Discovered CLI adapter: {} (command={}, format={})",
                    config.cliId(), config.command(), config.outputFormat());
        } catch (Exception e) {
            log.error("Failed to load adapter from {}: {}", yamlPath.getFileName(), e.getMessage());
        }
    }
}

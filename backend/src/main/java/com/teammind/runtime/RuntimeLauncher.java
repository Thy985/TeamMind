package com.teammind.runtime;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * RuntimeLauncher — Runtime Core 启动编排器
 *
 * 架构不变量：此类不依赖 Spring Web / WebSocket / Controller。
 * 它只编排 {@link RuntimeLifecycle} 组件的初始化顺序。
 *
 * 使用方式：
 *   Spring Boot Host → RuntimeBootstrap @PostConstruct → RuntimeLauncher.initialize()
 *   CLI Host         → main() → RuntimeLauncher.initialize()
 *   Test Host        → @BeforeEach → RuntimeLauncher.initialize()
 *
 * 初始化顺序：
 *   1. PluginBootstrap    — 加载插件
 *   2. CLIDiscoveryService — 发现 CLI 适配器
 *   3. RecoveryService     — 恢复 in-flight executions
 */
@Slf4j
public class RuntimeLauncher {

    private final List<RuntimeLifecycle> components = new ArrayList<>();
    private boolean initialized = false;

    public RuntimeLauncher register(RuntimeLifecycle component) {
        components.add(component);
        return this;
    }

    public RuntimeLauncher registerAll(List<RuntimeLifecycle> lifecycleComponents) {
        components.addAll(lifecycleComponents);
        return this;
    }

    /**
     * 按注册顺序初始化所有 Runtime 组件
     */
    public void initialize() {
        if (initialized) {
            log.warn("RuntimeLauncher already initialized, skipping");
            return;
        }

        log.info("RuntimeLauncher: initializing {} component(s)", components.size());

        for (RuntimeLifecycle component : components) {
            String name = component.getClass().getSimpleName();
            try {
                log.info("RuntimeLauncher: initializing {}", name);
                component.initialize();
                log.info("RuntimeLauncher: {} initialized", name);
            } catch (Exception e) {
                log.error("RuntimeLauncher: {} initialization failed: {}", name, e.getMessage(), e);
            }
        }

        initialized = true;
        log.info("RuntimeLauncher: initialization complete");
    }

    /**
     * 关闭 Runtime（调用各组件的清理逻辑）
     */
    public void shutdown() {
        log.info("RuntimeLauncher: shutting down {} component(s)", components.size());
        for (int i = components.size() - 1; i >= 0; i--) {
            RuntimeLifecycle component = components.get(i);
            String name = component.getClass().getSimpleName();
            try {
                if (component instanceof AutoCloseable closeable) {
                    closeable.close();
                    log.info("RuntimeLauncher: {} closed", name);
                }
            } catch (Exception e) {
                log.warn("RuntimeLauncher: {} shutdown failed: {}", name, e.getMessage());
            }
        }
        initialized = false;
        log.info("RuntimeLauncher: shutdown complete");
    }

    public boolean isInitialized() {
        return initialized;
    }
}
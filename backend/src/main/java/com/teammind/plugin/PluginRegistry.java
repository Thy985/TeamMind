package com.teammind.plugin;

import com.teammind.event.EventBus;
import com.teammind.plugin.agent.ClaudeCodePlugin;
import com.teammind.plugin.agent.CodexPlugin;
import com.teammind.plugin.verifier.GitVerifier;
import com.teammind.plugin.verifier.TestRunnerVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

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
}

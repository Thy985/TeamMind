package com.teammind.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.List;

/**
 * RuntimeBootstrap — Spring Boot Host 的 Runtime 启动入口
 *
 * 收集所有 {@link RuntimeLifecycle} Bean，委托给 {@link RuntimeLauncher} 编排初始化。
 * 同时将 RuntimeLauncher 暴露为 Spring Bean，供其他组件注入。
 *
 * 架构不变量：此类属于 Host 层（adapter），不属于 Runtime Core。
 * 未来 CLI Host / Tauri Host 会有自己的 Bootstrap 实现。
 */
@Slf4j
@Configuration
public class RuntimeBootstrap {

    private RuntimeLauncher launcher;

    @Bean
    public RuntimeLauncher runtimeLauncher(List<RuntimeLifecycle> lifecycleComponents) {
        launcher = new RuntimeLauncher().registerAll(lifecycleComponents);
        launcher.initialize();
        return launcher;
    }

    @PostConstruct
    public void start() {
        // initialize() 在 runtimeLauncher() @Bean 方法中调用
    }

    @PreDestroy
    public void stop() {
        if (launcher != null) {
            launcher.shutdown();
        }
    }
}

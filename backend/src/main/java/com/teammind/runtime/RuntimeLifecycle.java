package com.teammind.runtime;

/**
 * Runtime 生命周期 SPI
 *
 * 架构不变量：Runtime 组件通过此接口声明初始化逻辑，
 * 不直接依赖 Spring Boot 的 CommandLineRunner / ApplicationRunner。
 *
 * Host 负责在合适时机调用 initialize()：
 *   - Spring Boot Host：通过 @PostConstruct 或 RuntimeBootstrap 调用
 *   - CLI Host：RuntimeLauncher 手动调用
 *   - Test Host：测试 setup 中调用
 */
public interface RuntimeLifecycle {

    /**
     * 初始化 Runtime 组件
     *
     * @throws Exception 初始化失败
     */
    void initialize() throws Exception;
}
package com.teammind.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 统一线程池配置
 * 
 * 为任务执行引擎和 Agent 执行引擎提供一致的有界线程池，
 * 避免 newCachedThreadPool() 导致的无界线程 OOM 风险。
 */
@Configuration
public class ThreadPoolConfig {

    @Bean(name = "missionExecutorService")
    public ExecutorService missionExecutorService() {
        return createBoundedThreadPool("mission-executor");
    }

    @Bean(name = "agentExecutorService")
    public ExecutorService agentExecutorService() {
        return createBoundedThreadPool("agent-executor");
    }

    /**
     * 创建统一的有界线程池
     */
    private ExecutorService createBoundedThreadPool(String poolName) {
        int coreSize = Math.max(4, Runtime.getRuntime().availableProcessors());
        int maxSize = Math.max(8, Runtime.getRuntime().availableProcessors() * 2);

        return new ThreadPoolExecutor(
                coreSize,                                    // corePoolSize
                maxSize,                                     // maxPoolSize
                60,                                          // keepAliveTime
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),              // 有界队列，防止 OOM
                new ThreadFactory() {
                    private final AtomicInteger count = new AtomicInteger(0);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r);
                        t.setName(poolName + "-" + count.incrementAndGet());
                        t.setDaemon(false);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()     // 拒绝策略：调用者线程执行
        );
    }
}

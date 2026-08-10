package com.teammind.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.locks.ReentrantLock;

/**
 * SQLite 写串行化配置
 * 
 * SQLite 同一时刻仅允许一个写事务，多线程并发写会导致
 * "database is locked" 错误。本配置提供统一的写锁，
 * 确保所有数据库写操作串行执行。
 * 
 * 方案说明：
 * - 使用 {@link ReentrantLock} 实现可重入的写锁
 * - 锁为公平锁，避免写线程饥饿
 * - 写锁超时时间可配置（默认 30s，与 JDBC busy_timeout 一致）
 */
@Slf4j
@Configuration
public class SQLiteWriteLockConfig {

    /**
     * SQLite 全局写锁
     * 
     * 公平锁确保所有请求按 FIFO 顺序获得锁，避免某些写操作长期得不到执行。
     * 可重入锁允许同一线程内嵌套的写操作（如事务回调）。
     */
    @Bean(name = "sqliteWriteLock")
    public ReentrantLock sqliteWriteLock() {
        return new ReentrantLock(true); // 公平锁
    }

    /**
     * SQLite 写锁默认超时时间（毫秒）
     */
    public static final long WRITE_LOCK_TIMEOUT_MS = 30_000L;

    /**
     * 提供带超时的锁获取操作
     */
    public static boolean tryAcquireLock(ReentrantLock lock) {
        try {
            return lock.tryLock(WRITE_LOCK_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while acquiring SQLite write lock", e);
            return false;
        }
    }
}

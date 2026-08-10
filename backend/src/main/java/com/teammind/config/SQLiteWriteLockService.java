package com.teammind.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * SQLite 写锁服务
 * 
 * 提供统一的写串行化能力。SQLite 同一时刻仅允许一个写事务，
 * 多线程并发写会导致 "database is locked" 错误。
 * 
 * 用法：
 * <pre>
 *   writeLockService.executeWithLock(() -> {
 *       repository.save(entity);
 *       return result;
 *   });
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SQLiteWriteLockService {

    private final ReentrantLock sqliteWriteLock;

    /**
     * 在写锁保护下执行操作
     * 
     * @param supplier 写操作
     * @return 操作结果
     * @throws IllegalStateException 获取写锁超时（30s）
     */
    public <T> T executeWithLock(WriteOperation<T> supplier) {
        if (!SQLiteWriteLockConfig.tryAcquireLock(sqliteWriteLock)) {
            throw new IllegalStateException(
                    "Failed to acquire SQLite write lock within " +
                    SQLiteWriteLockConfig.WRITE_LOCK_TIMEOUT_MS + "ms");
        }
        try {
            return supplier.execute();
        } finally {
            sqliteWriteLock.unlock();
        }
    }

    /**
     * 在写锁保护下执行无返回值操作
     */
    public void executeWithLock(Runnable operation) {
        executeWithLock(() -> {
            operation.run();
            return null;
        });
    }

    /**
     * 写操作接口
     */
    @FunctionalInterface
    public interface WriteOperation<T> {
        T execute();
    }
}

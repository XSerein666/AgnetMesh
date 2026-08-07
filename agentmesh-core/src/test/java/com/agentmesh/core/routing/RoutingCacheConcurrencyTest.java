package com.agentmesh.core.routing;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RoutingCache 并发安全测试。
 * 验证 16d-4 修复（put + evictOldest 加 synchronized）：
 *   - 多线程并发 put 不超过 maxSize
 *   - put + get 并发执行不抛 NPE
 */
class RoutingCacheConcurrencyTest {

    @Test
    void concurrentPut_shouldNotExceedMaxSize() throws Exception {
        int maxSize = 100;
        RoutingCache cache = new RoutingCache(maxSize, Duration.ofMinutes(10));
        int threadCount = 20;
        int putsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicReference<Throwable> error = new AtomicReference<>();

        for (int t = 0; t < threadCount; t++) {
            final int offset = t * putsPerThread;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < putsPerThread; i++) {
                        cache.put("key-" + (offset + i), List.of());
                    }
                } catch (Throwable e) {
                    error.compareAndSet(null, e);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).as("所有 put 线程应在 30s 内完成").isTrue();
        assertThat(error.get()).as("并发 put 不应抛异常").isNull();

        // 核心：size 必须 <= maxSize，不会因为 put+evict 不原子而越界
        assertThat(cache.size())
                .as("并发写入后 cache.size 不应超过 maxSize")
                .isLessThanOrEqualTo(maxSize);
    }

    @Test
    void concurrentPutAndGet_shouldNotThrowNPE() throws Exception {
        RoutingCache cache = new RoutingCache(50, Duration.ofMinutes(10));
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);
        AtomicReference<Throwable> error = new AtomicReference<>();

        // 5 个 writer + 5 个 reader 并发
        for (int i = 0; i < 5; i++) {
            final int offset = i * 100;
            // writer
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        cache.put("k-" + (offset + j), List.of());
                    }
                } catch (Throwable e) {
                    error.compareAndSet(null, e);
                } finally {
                    latch.countDown();
                }
            });
            // reader
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        cache.get("k-" + (offset + j));
                    }
                } catch (Throwable e) {
                    error.compareAndSet(null, e);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).as("所有线程应在 30s 内完成").isTrue();
        assertThat(error.get()).as("并发 put + get 不应抛异常").isNull();
    }
}

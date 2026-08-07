package com.agentmesh.core.collaboration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SharedContext 单元测试。
 * 覆盖并发读写、CAS 原子更新、compute 原子操作、快照一致性、权限校验。
 */
class SharedContextTest {

    private SharedContext context;

    @BeforeEach
    void setUp() {
        context = new SharedContext("test-collab-1");
    }

    // ========== 基本读写 ==========

    @Test
    void shouldPutAndGet() {
        context.put("shared/test", "hello", "agent-1", "worker");
        assertEquals("hello", context.get("shared/test", String.class).orElse(null));
    }

    @Test
    void shouldReturnEmptyForNonExistentKey() {
        assertTrue(context.get("nonexistent", String.class).isEmpty());
    }

    @Test
    void shouldRemoveKey() {
        context.put("shared/test", "value", "agent-1", "worker");
        context.remove("shared/test");
        assertTrue(context.get("shared/test", String.class).isEmpty());
    }

    @Test
    void shouldClearAll() {
        context.put("shared/a", "1", "agent-1", "worker");
        context.put("shared/b", "2", "agent-1", "worker");
        context.clear();
        assertTrue(context.snapshot().isEmpty());
    }

    // ========== 快照 ==========

    @Test
    void shouldReturnImmutableSnapshot() {
        context.put("shared/a", "1", "agent-1", "worker");
        Map<String, Object> snapshot = context.snapshot();
        assertEquals(1, snapshot.size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("new", "val"));
    }

    // ========== CAS 原子更新 ==========

    @Test
    void shouldCompareAndSetSuccessfully() {
        context.put("shared/counter", 1, "agent-1", "worker");
        boolean updated = context.compareAndSet("shared/counter", 1, 2);
        assertTrue(updated);
        assertEquals(2, context.get("shared/counter", Integer.class).orElse(0));
    }

    @Test
    void shouldFailCompareAndSetWhenValueChanged() {
        context.put("shared/counter", 1, "agent-1", "worker");
        boolean updated = context.compareAndSet("shared/counter", 999, 2);
        assertFalse(updated);
        assertEquals(1, context.get("shared/counter", Integer.class).orElse(0));
    }

    // ========== compute 原子操作 ==========

    @Test
    void shouldComputeAtomically() {
        context.put("shared/counter", 1, "agent-1", "worker");
        context.compute("shared/counter", (k, v) -> ((Integer) v) + 1);
        assertEquals(2, context.get("shared/counter", Integer.class).orElse(0));
    }

    // ========== 并发测试 ==========

    @Test
    void shouldHandleConcurrentCAS() throws Exception {
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        context.put("shared/counter", 0, "agent-1", "worker");

        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    // 每个线程尝试多次 CAS
                    for (int j = 0; j < 100; j++) {
                        Object current = context.get("shared/counter", Integer.class).orElse(0);
                        if (context.compareAndSet("shared/counter", current, ((Integer) current) + 1)) {
                            successCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await();
        int finalValue = context.get("shared/counter", Integer.class).orElse(0);
        assertEquals(successCount.get(), finalValue,
                "CAS 成功次数应等于最终值（无丢失更新）");
    }

    @Test
    void shouldHandleConcurrentReadWrite() throws Exception {
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            new Thread(() -> {
                try {
                    String key = "shared/key-" + (idx % 5);
                    context.put(key, "value-" + idx, "agent-1", "worker");
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await();
        // 无异常即通过
        assertTrue(context.snapshot().size() <= 5);
    }

    // ========== 权限校验 ==========

    @Test
    void shouldAllowSupervisorToWriteSupervisorPrefix() {
        context.put("supervisor/plan", "plan-data", "supervisor-1", "supervisor");
        assertEquals("plan-data", context.get("supervisor/plan", String.class).orElse(null));
    }

    @Test
    void shouldRejectWorkerWritingSupervisorPrefix() {
        assertThrows(SecurityException.class, () -> {
            context.put("supervisor/plan", "evil", "worker-1", "worker");
        });
    }

    @Test
    void shouldAllowWorkerToWriteOwnPrefix() {
        context.put("worker/designer/result", "done", "designer", "worker");
        assertEquals("done", context.get("worker/designer/result", String.class).orElse(null));
    }

    @Test
    void shouldRejectWorkerWritingOtherWorkerPrefix() {
        assertThrows(SecurityException.class, () -> {
            context.put("worker/other/result", "evil", "designer", "worker");
        });
    }

    @Test
    void shouldRejectNullRoleWritingProtectedPrefix() {
        assertThrows(SecurityException.class, () -> {
            context.put("supervisor/plan", "evil", "agent-1", null);
        });
    }

    @Test
    void shouldRejectUnknownPrefix() {
        assertThrows(SecurityException.class, () -> {
            context.put("global/config", "value", "agent-1", "worker");
        });
    }

    @Test
    void shouldRejectAgentWritingSystemPrefix() {
        assertThrows(SecurityException.class, () -> {
            context.put("system/status", "ready", "agent-1", "worker");
        });
    }

    @Test
    void shouldAllowSharedPrefixForAnyRole() {
        context.put("shared/data", "public", "agent-1", "worker");
        context.put("shared/config", "public", "agent-2", "supervisor");
        assertEquals("public", context.get("shared/data", String.class).orElse(null));
        assertEquals("public", context.get("shared/config", String.class).orElse(null));
    }
}
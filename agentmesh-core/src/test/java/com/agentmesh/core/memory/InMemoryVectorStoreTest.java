package com.agentmesh.core.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryVectorStoreTest {

    private InMemoryVectorStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryVectorStore();
    }

    @Test
    void shouldStoreAndRetrieve() {
        MemoryItem item = createItem("m1", "s1", "用户喜欢简约风格", "PREFERENCE");
        store.store(item);

        List<MemoryItem> results = store.search("简约风格", 5);
        assertEquals(1, results.size());
        assertEquals("用户喜欢简约风格", results.get(0).getContent());
    }

    @Test
    void shouldSortByRelevance() {
        store.store(createItem("m1", "s1", "用户喜欢简约风格", "PREFERENCE"));
        store.store(createItem("m2", "s1", "钻石切割工艺", "FACT"));
        store.store(createItem("m3", "s1", "简约设计原则", "CONTEXT"));

        List<MemoryItem> results = store.search("简约", 5);

        assertFalse(results.isEmpty());
        // 最相关的应该排前面
        assertTrue(results.get(0).getScore() >= results.get(results.size() - 1).getScore());
    }

    @Test
    void shouldFilterBySessionId() {
        store.store(createItem("m1", "s1", "用户A偏好", "PREFERENCE"));
        store.store(createItem("m2", "s2", "用户B偏好", "PREFERENCE"));

        List<MemoryItem> results = store.search("偏好", 5, "s1", null);

        assertEquals(1, results.size());
        assertEquals("s1", results.get(0).getSessionId());
    }

    @Test
    void shouldFilterByType() {
        store.store(createItem("m1", "s1", "简约风格", "PREFERENCE"));
        store.store(createItem("m2", "s1", "预算5000", "FACT"));

        List<MemoryItem> results = store.search("简约", 5, null, "PREFERENCE");

        assertEquals(1, results.size());
        assertEquals("PREFERENCE", results.get(0).getType());
    }

    @Test
    void shouldNotRetrieveAfterDelete() {
        store.store(createItem("m1", "s1", "用户偏好", "PREFERENCE"));
        store.delete("m1");

        List<MemoryItem> results = store.search("偏好", 5);
        assertTrue(results.isEmpty());
    }

    @Test
    void shouldIsolateBySession() {
        store.store(createItem("m1", "s1", "用户A信息", "FACT"));
        store.store(createItem("m2", "s2", "用户B信息", "FACT"));

        List<MemoryItem> resultsA = store.search("信息", 5, "s1", null);
        List<MemoryItem> resultsB = store.search("信息", 5, "s2", null);

        assertEquals(1, resultsA.size());
        assertEquals(1, resultsB.size());
        assertNotEquals(resultsA.get(0).getContent(), resultsB.get(0).getContent());
    }

    @Test
    void shouldReturnEmptyForNoMatch() {
        store.store(createItem("m1", "s1", "钻石信息", "FACT"));

        List<MemoryItem> results = store.search("完全不相关", 5);
        // 相似度为 0 的结果被过滤
        assertEquals(0, results.size());
    }

    @Test
    void shouldLimitTopK() {
        for (int i = 0; i < 10; i++) {
            store.store(createItem("m" + i, "s1", "钻石相关信息 " + i, "FACT"));
        }

        List<MemoryItem> results = store.search("钻石", 3);
        assertEquals(3, results.size());
    }

    @Test
    void shouldClearBySession() {
        store.store(createItem("m1", "s1", "信息1", "FACT"));
        store.store(createItem("m2", "s1", "信息2", "FACT"));
        store.store(createItem("m3", "s2", "信息3", "FACT"));

        store.clearBySession("s1");

        assertEquals(0, store.search("信息", 5, "s1", null).size());
        assertEquals(1, store.search("信息", 5, "s2", null).size());
    }

    private MemoryItem createItem(String id, String sessionId, String content, String type) {
        return MemoryItem.builder()
                .id(id)
                .sessionId(sessionId)
                .content(content)
                .type(type)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
package com.agentmesh.core.tool.marketplace;

import com.agentmesh.core.tool.marketplace.health.CategoryRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CategoryRegistryTest {

    private CategoryRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new CategoryRegistry();
    }

    @Test
    void shouldHaveBuiltInCategories() {
        Collection<CategoryRegistry.Category> all = registry.getAll();
        assertTrue(all.size() >= 9);

        assertTrue(registry.exists("DATA_PROCESSING"));
        assertTrue(registry.exists("NETWORK"));
        assertTrue(registry.exists("FILE_OPERATION"));
        assertTrue(registry.exists("KNOWLEDGE"));
        assertTrue(registry.exists("CODE_EXECUTION"));
        assertTrue(registry.exists("MULTIMEDIA"));
        assertTrue(registry.exists("BUSINESS"));
        assertTrue(registry.exists("SYSTEM"));
        assertTrue(registry.exists("OTHER"));
    }

    @Test
    void shouldRegisterCustomCategory() {
        registry.register("CUSTOM_CAT", "自定义分类", "A custom category");
        assertTrue(registry.exists("CUSTOM_CAT"));
    }

    @Test
    void shouldFindByKey() {
        Optional<CategoryRegistry.Category> cat = registry.findByKey("DATA_PROCESSING");
        assertTrue(cat.isPresent());
        assertEquals("数据处理", cat.get().displayName());
    }

    @Test
    void shouldReturnEmptyForNonExistentKey() {
        assertTrue(registry.findByKey("NONEXISTENT").isEmpty());
    }

    @Test
    void shouldReturnFalseForNonExistentCategory() {
        assertFalse(registry.exists("NONEXISTENT"));
    }

    @Test
    void shouldReturnUnmodifiableCollection() {
        Collection<CategoryRegistry.Category> all = registry.getAll();
        assertThrows(UnsupportedOperationException.class, () -> all.clear());
    }
}
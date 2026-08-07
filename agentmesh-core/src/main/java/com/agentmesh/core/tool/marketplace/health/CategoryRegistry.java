package com.agentmesh.core.tool.marketplace.health;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具分类注册表。
 * 可动态扩展，支持内置分类和自定义分类。
 */
public class CategoryRegistry {

    private final Map<String, Category> categories = new ConcurrentHashMap<>();

    public CategoryRegistry() {
        // 内置分类
        register("DATA_PROCESSING", "数据处理", "数据转换、清洗、聚合等");
        register("NETWORK", "网络请求", "HTTP 调用、API 集成等");
        register("FILE_OPERATION", "文件操作", "文件读写、格式转换等");
        register("KNOWLEDGE", "知识检索", "RAG、向量搜索、文档问答等");
        register("CODE_EXECUTION", "代码执行", "代码运行、脚本执行等");
        register("MULTIMEDIA", "多媒体", "图片、音频、视频处理");
        register("BUSINESS", "业务领域", "行业特定工具（珠宝、金融、医疗等）");
        register("SYSTEM", "系统工具", "定时任务、通知、日志等");
        register("OTHER", "其他", "未分类工具");
    }

    /**
     * 注册自定义分类。
     */
    public void register(String key, String displayName, String description) {
        categories.put(key, new Category(key, displayName, description));
    }

    /**
     * 获取所有分类。
     */
    public Collection<Category> getAll() {
        return Collections.unmodifiableCollection(categories.values());
    }

    /**
     * 按 key 查找分类。
     */
    public Optional<Category> findByKey(String key) {
        return Optional.ofNullable(categories.get(key));
    }

    /**
     * 检查分类是否存在。
     */
    public boolean exists(String key) {
        return categories.containsKey(key);
    }

    public record Category(String key, String displayName, String description) {}
}

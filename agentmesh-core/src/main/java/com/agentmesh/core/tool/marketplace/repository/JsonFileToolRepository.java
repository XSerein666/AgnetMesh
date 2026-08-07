package com.agentmesh.core.tool.marketplace.repository;

import com.agentmesh.core.tool.marketplace.model.ToolMetadata;
import com.agentmesh.core.tool.marketplace.model.ToolVersion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * JSON 文件持久化工具仓库。
 * 启动时从文件加载，shutdown 时写入文件。
 * 文件路径：{data.dir}/tool-marketplace.json
 *
 * 写缓冲策略：
 * - 每次 save() 不立即刷盘，而是累积写操作
 * - 满足以下任一条件时触发刷盘：① 累积 10 次写操作 ② 距上次刷盘超过 5 秒
 * - 使用 ScheduledExecutorService 自管理定时刷盘，不依赖 Spring 的 @Scheduled
 * - 使用 @PreDestroy 确保 shutdown 时最后刷盘一次
 */
@Slf4j
public class JsonFileToolRepository implements ToolRepository {

    private static final String DEFAULT_FILE_NAME = "tool-marketplace.json";
    private static final int WRITE_BUFFER_THRESHOLD = 10;
    private static final long FLUSH_INTERVAL_MS = 5000;

    private final Path filePath;
    private final ObjectMapper objectMapper;
    private final InMemoryToolRepository delegate;

    /** 写操作计数器 */
    private final AtomicInteger writeCount = new AtomicInteger(0);
    /** 上次刷盘时间 */
    private volatile long lastFlushTime = System.currentTimeMillis();
    /** 刷盘锁（防止并发刷盘） */
    private final ReentrantLock flushLock = new ReentrantLock();
    /** 定时刷盘调度器（自管理，不依赖 Spring @Scheduled） */
    private final ScheduledExecutorService flushScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "tool-marketplace-flush");
                t.setDaemon(true);
                return t;
            });

    public JsonFileToolRepository(InMemoryToolRepository delegate, String dataDir) {
        this.delegate = delegate;
        this.filePath = Paths.get(dataDir, DEFAULT_FILE_NAME);
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        flushScheduler.scheduleWithFixedDelay(
                this::flushIfNeeded,
                FLUSH_INTERVAL_MS,
                FLUSH_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
        log.info("[JsonFileRepo] 定时刷盘任务已启动，间隔 {}ms", FLUSH_INTERVAL_MS);
    }

    @Override public ToolMetadata save(ToolMetadata metadata) {
        ToolMetadata result = delegate.save(metadata);
        onWrite();
        return result;
    }

    @Override
    public Optional<ToolMetadata> findById(String toolId) {
        return delegate.findById(toolId);
    }
    @Override
    public Optional<ToolMetadata> findByIdAndVersion(String toolId, ToolVersion version) {
        return delegate.findByIdAndVersion(toolId, version);
    }
    @Override
    public List<ToolMetadata> findAllVersions(String toolId) {
        return delegate.findAllVersions(toolId);
    }
    @Override
    public List<ToolMetadata> findAllPublished() {
        return delegate.findAllPublished();
    }
    @Override
    public List<ToolMetadata> findByCategory(String category) {
        return delegate.findByCategory(category);
    }
    @Override
    public List<ToolMetadata> findByTag(String tag) {
        return delegate.findByTag(tag);
    }
    @Override
    public List<ToolMetadata> findByPublisher(String publisher) {
        return delegate.findByPublisher(publisher);
    }
    @Override
    public SearchResult search(String keyword, int offset, int limit) {
        return delegate.search(keyword, offset, limit);
    }

    @Override
    public void deleteById(String toolId) {
        delegate.deleteById(toolId);
        onWrite();
    }

    @Override
    public void deleteByIdAndVersion(String toolId, ToolVersion version) {
        delegate.deleteByIdAndVersion(toolId, version);
        onWrite();
    }

    @Override
    public long count() {
        return delegate.count();
    }

    /**
     * 记录一次写操作，到达阈值时触发刷盘。
     */
    public void onWrite() {
        int count = writeCount.incrementAndGet();
        long elapsed = System.currentTimeMillis() - lastFlushTime;
        if (count >= WRITE_BUFFER_THRESHOLD || elapsed >= FLUSH_INTERVAL_MS) {
            flushIfNeeded();
        }
    }

    /**
     * 条件刷盘：仅在达到阈值或超时时执行。
     */
    private void flushIfNeeded() {
        if (flushLock.tryLock()) {
            try {
                int count = writeCount.get();
                long elapsed = System.currentTimeMillis() - lastFlushTime;
                if (count >= WRITE_BUFFER_THRESHOLD || elapsed >= FLUSH_INTERVAL_MS) {
                    doFlush();
                    writeCount.set(0);
                    lastFlushTime = System.currentTimeMillis();
                }
            } finally {
                flushLock.unlock();
            }
        }
    }

    /**
     * 强制刷盘（shutdown 时调用）。
     */
    public void forceFlush() {
        flushLock.lock();
        try {
            doFlush();
            writeCount.set(0);
            lastFlushTime = System.currentTimeMillis();
        } finally {
            flushLock.unlock();
        }
    }

    /**
     * 从 JSON 文件加载所有工具元数据到内存仓库。
     */
    @Override
    public void flush() {
        forceFlush();
    }

    @Override
    public void load() {
        if (!Files.exists(filePath)) {
            log.info("[JsonFileRepo] 数据文件不存在，跳过加载: {}", filePath);
            return;
        }
        try {
            String content = Files.readString(filePath);
            if (content.isBlank()) {
                return;
            }
            List<ToolMetadata> items = objectMapper.readValue(content,
                    new TypeReference<List<ToolMetadata>>() {});
            for (ToolMetadata item : items) {
                delegate.save(item);
            }
            log.info("[JsonFileRepo] 加载完成: {} 个工具", items.size());
        } catch (IOException e) {
            log.error("[JsonFileRepo] 加载失败: {}", filePath, e);
        }
    }

    /**
     * 将内存仓库中的所有工具元数据写入 JSON 文件。
     */
    private void doFlush() {
        try {
            List<ToolMetadata> all = new ArrayList<>();
            for (List<ToolMetadata> versions : delegate.getAllVersionsInternal()) {
                all.addAll(versions);
            }
            Path parent = filePath.getParent();
            if (parent == null) {
                log.error("[JsonFileRepo] 无法获取父目录: {}", filePath);
                return;
            }
            Files.createDirectories(parent);
            Path tmpPath = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmpPath.toFile(), all);
            Files.move(tmpPath, filePath, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            log.info("[JsonFileRepo] 持久化完成: {} 个工具版本", all.size());
        } catch (IOException e) {
            log.error("[JsonFileRepo] 持久化失败: {}", filePath, e);
        }
    }

    /**
     * Shutdown 钩子：确保最后一次刷盘，并关闭定时调度器。
     */
    @jakarta.annotation.PreDestroy
    public void onShutdown() {
        log.info("[JsonFileRepo] 应用关闭，执行最终刷盘...");
        forceFlush();
        flushScheduler.shutdown();
        try {
            if (!flushScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                flushScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            flushScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

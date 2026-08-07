package com.agentmesh.core.collaboration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 文件存储实现的工作流状态持久化。
 *
 * 状态文件存储在 {workflow.state.dir}/collaborationId.json。
 * 可通过 agentmesh.collaboration.hitl.state-dir 配置存储目录。
 */
@Slf4j
public class FileWorkflowStateStore implements WorkflowStateStore {

    private final Path stateDir;
    private final ObjectMapper objectMapper;

    public FileWorkflowStateStore(String stateDir) {
        this.stateDir = Paths.get(stateDir);
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        try {
            Files.createDirectories(this.stateDir);
            log.info("[FileWorkflowStateStore] 状态存储目录: {}", this.stateDir.toAbsolutePath());
        } catch (IOException e) {
            log.error("[FileWorkflowStateStore] 创建状态存储目录失败: {}", this.stateDir, e);
        }
    }

    @Override
    public void save(String collaborationId, WorkflowState state) {
        try {
            Path filePath = getFilePath(collaborationId);
            objectMapper.writeValue(filePath.toFile(), state);
            log.info("[FileWorkflowStateStore] 保存状态: collaborationId={}, file={}",
                    collaborationId, filePath);
        } catch (IOException e) {
            log.error("[FileWorkflowStateStore] 保存状态失败: collaborationId={}", collaborationId, e);
            throw new RuntimeException("保存工作流状态失败: " + collaborationId, e);
        }
    }

    @Override
    public Optional<WorkflowState> load(String collaborationId) {
        try {
            Path filePath = getFilePath(collaborationId);
            if (!Files.exists(filePath)) {
                log.debug("[FileWorkflowStateStore] 状态文件不存在: collaborationId={}", collaborationId);
                return Optional.empty();
            }
            WorkflowState state = objectMapper.readValue(filePath.toFile(), WorkflowState.class);

            // 检查是否过期
            if (state.getExpireTime() != null && state.getExpireTime().isBefore(Instant.now())) {
                log.warn("[FileWorkflowStateStore] 状态已过期: collaborationId={}, expireTime={}",
                        collaborationId, state.getExpireTime());
                delete(collaborationId);
                return Optional.empty();
            }

            log.info("[FileWorkflowStateStore] 加载状态: collaborationId={}, status={}",
                    collaborationId, state.getStatus());
            return Optional.of(state);
        } catch (IOException e) {
            log.error("[FileWorkflowStateStore] 加载状态失败: collaborationId={}", collaborationId, e);
            return Optional.empty();
        }
    }

    @Override
    public void delete(String collaborationId) {
        try {
            Path filePath = getFilePath(collaborationId);
            Files.deleteIfExists(filePath);
            log.info("[FileWorkflowStateStore] 删除状态: collaborationId={}", collaborationId);
        } catch (IOException e) {
            log.warn("[FileWorkflowStateStore] 删除状态失败: collaborationId={}", collaborationId, e.getMessage());
        }
    }

    @Override
    public int cleanupExpired(Duration maxAge) {
        int count = 0;
        try {
            Instant cutoff = Instant.now().minus(maxAge);
            var files = Files.list(stateDir).filter(Files::isRegularFile).toList();
            for (Path file : files) {
                try {
                    WorkflowState state = objectMapper.readValue(file.toFile(), WorkflowState.class);
                    if (state.getRequestTime() != null && state.getRequestTime().isBefore(cutoff)) {
                        Files.deleteIfExists(file);
                        count++;
                        log.debug("[FileWorkflowStateStore] 清理过期状态: file={}", file.getFileName());
                    }
                } catch (IOException e) {
                    log.warn("[FileWorkflowStateStore] 清理时读取失败: file={}", file.getFileName(), e);
                }
            }
        } catch (IOException e) {
            log.error("[FileWorkflowStateStore] 清理过期状态失败", e);
        }
        log.info("[FileWorkflowStateStore] 清理完成: {} 个过期状态", count);
        return count;
    }

    private Path getFilePath(String collaborationId) {
        return stateDir.resolve(collaborationId + ".json");
    }
}

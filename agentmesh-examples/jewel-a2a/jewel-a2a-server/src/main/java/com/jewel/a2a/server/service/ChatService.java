package com.jewel.a2a.server.service;

import com.agentmesh.core.agent.ReActAgent;
import com.agentmesh.core.protocol.ChatRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jewel.a2a.common.dto.ChatMessage;
import com.jewel.a2a.common.dto.ChatResponse;
import com.jewel.a2a.common.enums.TaskStatus;
import com.jewel.a2a.repository.entity.ConversationEntity;
import com.jewel.a2a.repository.entity.TaskEntity;
import com.jewel.a2a.repository.mapper.ConversationMapper;
import com.jewel.a2a.repository.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 聊天服务：会话管理 + AgentMesh ReActAgent 调度 + 异步任务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String SYSTEM_PROMPT = """
            你是一个珠宝定制智能助手。你可以使用以下工具来帮助用户：

            1. generate_jewelry_design - 生成珠宝设计图
               输入: { "prompt": "设计描述" }
               用途: 当用户想要设计一款珠宝时使用

            2. check_craft_feasibility - 校验工艺可行性
               输入: { "imageUrl": "设计图URL" }
               用途: 当用户想检查设计是否可生产时使用

            3. search_craft_knowledge - 检索工艺知识
               输入: { "query": "检索关键词" }
               用途: 当用户询问珠宝工艺知识时使用

            4. analyze_jewelry_image - 分析珠宝图片
               输入: { "imageUrl": "图片URL" }
               用途: 当用户上传珠宝图片需要分析时使用

            请用专业、友好的语气，用中文回复。""";

    private final ConversationMapper conversationMapper;
    private final ConversationStore conversationStore;
    private final ReActAgent reActAgent;
    private final TaskMapper taskMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 提交聊天任务，秒级返回
     */
    @Transactional
    public ChatResponse submitChat(ChatRequest request) {
        String sessionId = request.getSessionId() != null
                ? request.getSessionId()
                : "chat_" + UUID.randomUUID().toString().substring(0, 8);
        String taskId = "task_" + UUID.randomUUID().toString().substring(0, 8);
        LocalDateTime now = LocalDateTime.now();

        // 创建任务
        TaskEntity task = TaskEntity.builder()
                .taskId(taskId)
                .skillId("chat")
                .input(Map.of("sessionId", sessionId, "message", request.getMessage()))
                .status(TaskStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();
        taskMapper.insert(task);

        log.info("[ChatService] 聊天任务已提交: sessionId={}, taskId={}", sessionId, taskId);

        // 异步执行
        executeChat(taskId, sessionId, request.getMessage());

        return ChatResponse.builder()
                .sessionId(sessionId)
                .taskId(taskId)
                .status("PENDING")
                .message("任务已提交")
                .build();
    }

    @Async
    public void executeChat(String taskId, String sessionId, String message) {
        try {
            updateTask(taskId, TaskStatus.RUNNING, null);

            // 加载历史
            List<ChatMessage> history = conversationStore.getHistory(sessionId);

            // 转换为 AgentMesh 的 ChatMessage 类型
            List<com.agentmesh.core.session.ChatMessage> agentMeshHistory = history.stream()
                    .map(m -> com.agentmesh.core.session.ChatMessage.builder()
                            .role(m.getRole())
                            .content(m.getContent())
                            .toolName(m.getToolName())
                            .build())
                    .collect(Collectors.toList());

            // 调用 AgentMesh ReActAgent
            ReActAgent.AgentResult agentResult = reActAgent.run(SYSTEM_PROMPT, message, agentMeshHistory);

            // 保存消息
            conversationStore.append(sessionId,
                    ChatMessage.builder().role("user").content(message).build());
            conversationStore.append(sessionId,
                    ChatMessage.builder().role("assistant").content(agentResult.reply).build());

            // 持久化到数据库
            persistConversation(sessionId, conversationStore.getHistory(sessionId));

            // 构建输出
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("sessionId", sessionId);
            output.put("reply", agentResult.reply);
            output.put("toolCalls", agentResult.toolCalls);

            updateTask(taskId, TaskStatus.SUCCESS, output);
            log.info("[ChatService] 聊天完成: sessionId={}, taskId={}", sessionId, taskId);

        } catch (Exception e) {
            log.error("[ChatService] 聊天失败: sessionId={}", sessionId, e);
            updateTask(taskId, TaskStatus.FAILED, Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取会话历史
     */
    public List<ChatMessage> getHistory(String sessionId) {
        // 优先从缓存读取
        List<ChatMessage> history = conversationStore.getHistory(sessionId);
        if (!history.isEmpty()) {
            return history;
        }
        // 从数据库恢复
        ConversationEntity entity = conversationMapper.findBySessionId(sessionId);
        if (entity != null && entity.getMessages() != null) {
            try {
                ChatMessage[] msgs = objectMapper.convertValue(entity.getMessages(), ChatMessage[].class);
                List<ChatMessage> list = new ArrayList<>(Arrays.asList(msgs));
                for (ChatMessage msg : list) {
                    conversationStore.append(sessionId, msg);
                }
                return list;
            } catch (Exception e) {
                log.warn("[ChatService] 会话历史解析失败: sessionId={}", sessionId, e);
            }
        }
        return Collections.emptyList();
    }

    private void persistConversation(String sessionId, List<ChatMessage> history) {
        try {
            String json = objectMapper.writeValueAsString(history);
            ConversationEntity exist = conversationMapper.findBySessionId(sessionId);
            if (exist != null) {
                exist.setMessages(json);
                exist.setUpdatedAt(LocalDateTime.now());
                conversationMapper.updateById(exist);
            } else {
                ConversationEntity entity = new ConversationEntity();
                entity.setSessionId(sessionId);
                entity.setMessages(json);
                entity.setCreatedAt(LocalDateTime.now());
                entity.setUpdatedAt(LocalDateTime.now());
                conversationMapper.insert(entity);
            }
        } catch (Exception e) {
            log.warn("[ChatService] 会话持久化失败: sessionId={}", sessionId, e);
        }
    }

    private void updateTask(String taskId, TaskStatus status, Object output) {
        TaskEntity entity = TaskEntity.builder()
                .status(status)
                .output(output)
                .updatedAt(LocalDateTime.now())
                .build();
        taskMapper.update(entity,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TaskEntity>()
                        .eq(TaskEntity::getTaskId, taskId));
    }
}
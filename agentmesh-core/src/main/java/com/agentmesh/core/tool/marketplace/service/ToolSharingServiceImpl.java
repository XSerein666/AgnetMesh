package com.agentmesh.core.tool.marketplace.service;

import com.agentmesh.core.agent.AgentConfig;
import com.agentmesh.core.collaboration.AgentMessage;
import com.agentmesh.core.collaboration.MessageBus;
import com.agentmesh.core.registry.AgentNode;
import com.agentmesh.core.registry.AgentRegistry;
import com.agentmesh.core.tool.marketplace.exception.ToolMarketplaceException;
import com.agentmesh.core.tool.marketplace.model.ToolMetadata;
import com.agentmesh.core.tool.marketplace.model.ToolSharingMessage;
import com.agentmesh.core.tool.marketplace.repository.ToolRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跨 Agent 工具共享服务实现。
 */
@Slf4j
public class ToolSharingServiceImpl implements ToolSharingService {

    private final MessageBus messageBus;
    private final ToolMarketplace marketplace;
    private final ToolRepository toolRepository;
    private final AgentRegistry agentRegistry;
    private final AgentConfig agentConfig;
    private final ObjectMapper objectMapper;

    /** 已发现的远程 Agent 工具缓存 */
    private final Map<String, List<ToolSharingMessage>> remoteToolCache = new ConcurrentHashMap<>();

    public ToolSharingServiceImpl(MessageBus messageBus,
                                   ToolMarketplace marketplace,
                                   ToolRepository toolRepository,
                                   AgentRegistry agentRegistry,
                                   AgentConfig agentConfig,
                                   ObjectMapper objectMapper) {
        this.messageBus = messageBus;
        this.marketplace = marketplace;
        this.toolRepository = toolRepository;
        this.agentRegistry = agentRegistry;
        this.agentConfig = agentConfig;
        this.objectMapper = objectMapper;
    }

    private String getCurrentAgentId() {
        return agentConfig.getAgentId();
    }

    @Override
    public Mono<String> publishTool(ToolSharingMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            AgentMessage msg = AgentMessage.builder()
                    .messageId(UUID.randomUUID().toString())
                    .fromAgentId(message.getPublisher())
                    .type(AgentMessage.MessageType.CONTEXT_UPDATE)
                    .content("TOOL_PUBLISHED")
                    .payload(Map.of("toolSharingMessage", payload))
                    .timestamp(Instant.now())
                    .build();
            messageBus.registerAgentRole(getCurrentAgentId(), agentConfig.getRole());
            return messageBus.broadcast(msg, null);
        } catch (JsonProcessingException e) {
            return Mono.error(new ToolMarketplaceException(
                    "工具消息序列化失败", message.getToolId(), e));
        }
    }

    @Override
    public Mono<String> updateTool(ToolSharingMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            AgentMessage msg = AgentMessage.builder()
                    .messageId(UUID.randomUUID().toString())
                    .fromAgentId(message.getPublisher())
                    .type(AgentMessage.MessageType.CONTEXT_UPDATE)
                    .content("TOOL_UPDATED")
                    .payload(Map.of("toolSharingMessage", payload))
                    .timestamp(Instant.now())
                    .build();
            return messageBus.broadcast(msg, null);
        } catch (JsonProcessingException e) {
            return Mono.error(new ToolMarketplaceException(
                    "工具消息序列化失败", message.getToolId(), e));
        }
    }

    @Override
    public Mono<String> deprecateTool(ToolSharingMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            AgentMessage msg = AgentMessage.builder()
                    .messageId(UUID.randomUUID().toString())
                    .fromAgentId(message.getPublisher())
                    .type(AgentMessage.MessageType.CONTEXT_UPDATE)
                    .content("TOOL_DEPRECATED")
                    .payload(Map.of("toolSharingMessage", payload))
                    .timestamp(Instant.now())
                    .build();
            return messageBus.broadcast(msg, null);
        } catch (JsonProcessingException e) {
            return Mono.error(new ToolMarketplaceException(
                    "工具消息序列化失败", message.getToolId(), e));
        }
    }

    @Override
    public Flux<ToolSharingMessage> subscribe(String agentId) {
        return messageBus.subscribe(agentId)
                .filter(msg -> "TOOL_PUBLISHED".equals(msg.getContent())
                        || "TOOL_UPDATED".equals(msg.getContent())
                        || "TOOL_DEPRECATED".equals(msg.getContent()))
                .handle((msg, sink) -> {
                    try {
                        Map<String, Object> payload = msg.getPayload();
                        if (payload != null && payload.containsKey("toolSharingMessage")) {
                            String json = (String) payload.get("toolSharingMessage");
                            ToolSharingMessage sharingMsg = objectMapper.readValue(
                                    json, ToolSharingMessage.class);
                            sink.next(sharingMsg);
                        }
                    } catch (JsonProcessingException e) {
                        log.error("[ToolSharing] 消息反序列化失败: {}", msg.getMessageId(), e);
                        sink.error(new ToolMarketplaceException(
                                "工具消息反序列化失败", null, e));
                    }
                });
    }

    private List<ToolMetadata> fetchRemoteTools(String endpointUrl) {
        return List.of();
    }

    public void discoverAgents() {
        List<AgentNode> agents = agentRegistry.discover();
        for (AgentNode agent : agents) {
            String agentId = agent.getCard().getAgentId();
            if (!agentId.equals(getCurrentAgentId())) {
                try {
                    String endpointUrl = agent.getCard().getUrl();
                    List<ToolMetadata> remoteTools = fetchRemoteTools(endpointUrl);
                    remoteTools.forEach(toolRepository::save);
                    log.info("[ToolSharing] 发现 Agent {} 的工具: {} 个", agentId, remoteTools.size());
                } catch (Exception e) {
                    log.warn("[ToolSharing] 无法连接 Agent {}: {}", agentId, e.getMessage());
                }
            }
        }
    }

    @Override
    public ToolRepository.SearchResult searchGlobal(String keyword, int offset, int limit) {
        return marketplace.search(keyword, offset, limit);
    }
}

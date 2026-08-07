package com.agentmesh.core.tool.marketplace;

import com.agentmesh.core.agent.AgentConfig;
import com.agentmesh.core.collaboration.AgentMessage;
import com.agentmesh.core.collaboration.MessageBus;
import com.agentmesh.core.protocol.AgentCard;
import com.agentmesh.core.registry.AgentNode;
import com.agentmesh.core.registry.AgentRegistry;
import com.agentmesh.core.tool.marketplace.model.ToolSharingMessage;
import com.agentmesh.core.tool.marketplace.model.ToolVersion;
import com.agentmesh.core.tool.marketplace.repository.ToolRepository;
import com.agentmesh.core.tool.marketplace.service.ToolMarketplace;
import com.agentmesh.core.tool.marketplace.service.ToolSharingServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolSharingServiceImplTest {

    @Mock
    private MessageBus messageBus;
    @Mock
    private ToolMarketplace marketplace;
    @Mock
    private ToolRepository toolRepository;
    @Mock
    private AgentRegistry agentRegistry;
    @Mock
    private AgentConfig agentConfig;

    private ObjectMapper objectMapper;
    private ToolSharingServiceImpl sharingService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        lenient().when(agentConfig.getAgentId()).thenReturn("agent-1");
        lenient().when(agentConfig.getRole()).thenReturn("worker");

        sharingService = new ToolSharingServiceImpl(
                messageBus, marketplace, toolRepository, agentRegistry, agentConfig, objectMapper);
    }

    @Test
    void shouldPublishTool() {
        ToolSharingMessage msg = ToolSharingMessage.builder()
                .messageId("msg-1")
                .eventType(ToolSharingMessage.SharingEventType.TOOL_PUBLISHED)
                .toolId("agent-1:tool-a")
                .name("Tool A")
                .publisher("agent-1")
                .publisherEndpointUrl("http://agent-1:8080")
                .version(ToolVersion.parse("1.0.0"))
                .build();

        when(messageBus.broadcast(any(AgentMessage.class), eq(null))).thenReturn(Mono.just("ok"));

        Mono<String> result = sharingService.publishTool(msg);

        String messageId = result.block();
        assertNotNull(messageId);
        verify(messageBus).registerAgentRole("agent-1", "worker");
        verify(messageBus).broadcast(any(AgentMessage.class), eq(null));
    }

    @Test
    void shouldUpdateTool() {
        ToolSharingMessage msg = ToolSharingMessage.builder()
                .messageId("msg-2")
                .eventType(ToolSharingMessage.SharingEventType.TOOL_UPDATED)
                .toolId("agent-1:tool-a")
                .name("Tool A v2")
                .publisher("agent-1")
                .publisherEndpointUrl("http://agent-1:8080")
                .version(ToolVersion.parse("2.0.0"))
                .build();

        when(messageBus.broadcast(any(AgentMessage.class), eq(null))).thenReturn(Mono.just("ok"));

        Mono<String> result = sharingService.updateTool(msg);

        String messageId = result.block();
        assertNotNull(messageId);
    }

    @Test
    void shouldDeprecateTool() {
        ToolSharingMessage msg = ToolSharingMessage.builder()
                .messageId("msg-3")
                .eventType(ToolSharingMessage.SharingEventType.TOOL_DEPRECATED)
                .toolId("agent-1:tool-a")
                .name("Tool A")
                .publisher("agent-1")
                .publisherEndpointUrl("http://agent-1:8080")
                .version(ToolVersion.parse("1.0.0"))
                .build();

        when(messageBus.broadcast(any(AgentMessage.class), eq(null))).thenReturn(Mono.just("ok"));

        Mono<String> result = sharingService.deprecateTool(msg);

        String messageId = result.block();
        assertNotNull(messageId);
    }

    @Test
    void shouldSubscribeToToolMessages() throws Exception {
        ToolSharingMessage sharingMsg = ToolSharingMessage.builder()
                .messageId("msg-1")
                .eventType(ToolSharingMessage.SharingEventType.TOOL_PUBLISHED)
                .toolId("agent-2:tool-a")
                .name("Tool A")
                .publisher("agent-2")
                .version(ToolVersion.parse("1.0.0"))
                .build();

        String payload = objectMapper.writeValueAsString(sharingMsg);
        AgentMessage agentMsg = AgentMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .fromAgentId("agent-2")
                .type(AgentMessage.MessageType.CONTEXT_UPDATE)
                .content("TOOL_PUBLISHED")
                .payload(Map.of("toolSharingMessage", payload))
                .timestamp(Instant.now())
                .build();

        when(messageBus.subscribe("agent-1")).thenReturn(Flux.just(agentMsg));

        Flux<ToolSharingMessage> result = sharingService.subscribe("agent-1");

        StepVerifier.create(result)
                .expectNextMatches(msg -> "agent-2:tool-a".equals(msg.getToolId())
                        && "Tool A".equals(msg.getName()))
                .verifyComplete();
    }

    @Test
    void shouldFilterNonToolMessages() {
        AgentMessage nonToolMsg = AgentMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .fromAgentId("agent-2")
                .type(AgentMessage.MessageType.CONTEXT_UPDATE)
                .content("SOME_OTHER_MESSAGE")
                .payload(Map.of())
                .timestamp(Instant.now())
                .build();

        when(messageBus.subscribe("agent-1")).thenReturn(Flux.just(nonToolMsg));

        Flux<ToolSharingMessage> result = sharingService.subscribe("agent-1");

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void shouldSearchGlobal() {
        when(marketplace.search("keyword", 0, 20)).thenReturn(
                new ToolRepository.SearchResult(List.of(), 0));

        ToolRepository.SearchResult result = sharingService.searchGlobal("keyword", 0, 20);
        assertEquals(0, result.total());
    }

    @Test
    void shouldDiscoverAgents() {
        AgentCard card = mock(AgentCard.class);
        when(card.getAgentId()).thenReturn("agent-2");
        when(card.getUrl()).thenReturn("http://agent-2:8080");
        AgentNode node = mock(AgentNode.class);
        when(node.getCard()).thenReturn(card);
        when(agentRegistry.discover()).thenReturn(List.of(node));

        sharingService.discoverAgents();

        verify(agentRegistry).discover();
    }
}
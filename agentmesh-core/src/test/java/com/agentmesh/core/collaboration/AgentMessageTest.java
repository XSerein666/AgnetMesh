package com.agentmesh.core.collaboration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentMessage 单元测试。
 * 覆盖序列化/反序列化、MessageType 枚举、traceId 传递。
 */
class AgentMessageTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    void shouldCreateMessageWithAllFields() {
        AgentMessage msg = AgentMessage.builder()
                .messageId("msg-001")
                .fromAgentId("supervisor")
                .toAgentId("worker-1")
                .type(AgentMessage.MessageType.TASK_ASSIGNMENT)
                .content("请完成设计任务")
                .sessionId("session-1")
                .collaborationId("collab-1")
                .traceId("trace-001")
                .timestamp(Instant.now())
                .build();

        assertEquals("msg-001", msg.getMessageId());
        assertEquals("supervisor", msg.getFromAgentId());
        assertEquals("worker-1", msg.getToAgentId());
        assertEquals(AgentMessage.MessageType.TASK_ASSIGNMENT, msg.getType());
        assertEquals("请完成设计任务", msg.getContent());
        assertEquals("session-1", msg.getSessionId());
        assertEquals("collab-1", msg.getCollaborationId());
        assertEquals("trace-001", msg.getTraceId());
        assertNotNull(msg.getTimestamp());
    }

    @Test
    void shouldCreateMessageWithPayload() {
        Map<String, Object> payload = Map.of("subTaskId", "task-1", "priority", 1);
        AgentMessage msg = AgentMessage.builder()
                .messageId("msg-002")
                .fromAgentId("supervisor")
                .toAgentId("worker-1")
                .type(AgentMessage.MessageType.TASK_ASSIGNMENT)
                .payload(payload)
                .build();

        assertEquals("task-1", msg.getPayload().get("subTaskId"));
        assertEquals(1, msg.getPayload().get("priority"));
    }

    @Test
    void shouldSerializeAndDeserialize() throws Exception {
        AgentMessage original = AgentMessage.builder()
                .messageId("msg-003")
                .fromAgentId("supervisor")
                .toAgentId("worker-1")
                .type(AgentMessage.MessageType.TASK_COMPLETE)
                .content("任务完成")
                .collaborationId("collab-1")
                .traceId("trace-001")
                .timestamp(Instant.parse("2026-08-06T10:00:00Z"))
                .replyToMessageId("msg-001")
                .build();

        String json = objectMapper.writeValueAsString(original);
        AgentMessage deserialized = objectMapper.readValue(json, AgentMessage.class);

        assertEquals(original.getMessageId(), deserialized.getMessageId());
        assertEquals(original.getType(), deserialized.getType());
        assertEquals(original.getContent(), deserialized.getContent());
        assertEquals(original.getCollaborationId(), deserialized.getCollaborationId());
        assertEquals(original.getReplyToMessageId(), deserialized.getReplyToMessageId());
    }

    @Test
    void shouldHaveAllMessageTypes() {
        AgentMessage.MessageType[] types = AgentMessage.MessageType.values();
        assertEquals(12, types.length);
        assertNotNull(AgentMessage.MessageType.valueOf("TASK_ASSIGNMENT"));
        assertNotNull(AgentMessage.MessageType.valueOf("TASK_COMPLETE"));
        assertNotNull(AgentMessage.MessageType.valueOf("TASK_FAILED"));
        assertNotNull(AgentMessage.MessageType.valueOf("QUERY"));
        assertNotNull(AgentMessage.MessageType.valueOf("RESPONSE"));
        assertNotNull(AgentMessage.MessageType.valueOf("DELEGATE"));
        assertNotNull(AgentMessage.MessageType.valueOf("CONTEXT_UPDATE"));
        assertNotNull(AgentMessage.MessageType.valueOf("DEBATE_STATEMENT"));
        assertNotNull(AgentMessage.MessageType.valueOf("DEBATE_CHALLENGE"));
        assertNotNull(AgentMessage.MessageType.valueOf("APPROVAL_REQUEST"));
        assertNotNull(AgentMessage.MessageType.valueOf("APPROVAL_RESULT"));
        assertNotNull(AgentMessage.MessageType.valueOf("HEARTBEAT"));
    }

    @Test
    void shouldNotDefaultTimestamp() {
        // timestamp 不使用 @Builder.Default，避免反序列化覆盖
        AgentMessage msg = AgentMessage.builder()
                .messageId("msg-004")
                .fromAgentId("supervisor")
                .type(AgentMessage.MessageType.QUERY)
                .build();

        assertNull(msg.getTimestamp(), "timestamp 不应有默认值");
    }

    @Test
    void shouldSupportReplyChain() {
        AgentMessage request = AgentMessage.builder()
                .messageId("req-001")
                .fromAgentId("supervisor")
                .toAgentId("worker-1")
                .type(AgentMessage.MessageType.QUERY)
                .build();

        AgentMessage reply = AgentMessage.builder()
                .messageId("reply-001")
                .fromAgentId("worker-1")
                .toAgentId("supervisor")
                .type(AgentMessage.MessageType.RESPONSE)
                .replyToMessageId("req-001")
                .build();

        assertEquals("req-001", reply.getReplyToMessageId());
    }
}
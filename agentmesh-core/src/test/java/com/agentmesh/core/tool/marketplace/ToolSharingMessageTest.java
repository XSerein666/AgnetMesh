package com.agentmesh.core.tool.marketplace;

import com.agentmesh.core.tool.marketplace.model.ToolSharingMessage;
import com.agentmesh.core.tool.marketplace.model.ToolVersion;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ToolSharingMessageTest {

    @Test
    void shouldBuildPublishMessage() {
        Instant now = Instant.now();
        ToolSharingMessage msg = ToolSharingMessage.builder()
                .messageId("msg-1")
                .eventType(ToolSharingMessage.SharingEventType.TOOL_PUBLISHED)
                .toolId("pub:tool-a")
                .name("Tool A")
                .description("A useful tool")
                .category("DATA_PROCESSING")
                .version(ToolVersion.parse("1.0.0"))
                .publisher("pub")
                .publisherEndpointUrl("http://pub:8080")
                .timestamp(now)
                .build();

        assertEquals("msg-1", msg.getMessageId());
        assertEquals(ToolSharingMessage.SharingEventType.TOOL_PUBLISHED, msg.getEventType());
        assertEquals("pub:tool-a", msg.getToolId());
        assertEquals("Tool A", msg.getName());
        assertEquals("A useful tool", msg.getDescription());
        assertEquals("DATA_PROCESSING", msg.getCategory());
        assertEquals(ToolVersion.parse("1.0.0"), msg.getVersion());
        assertEquals("pub", msg.getPublisher());
        assertEquals("http://pub:8080", msg.getPublisherEndpointUrl());
        assertEquals(now, msg.getTimestamp());
    }

    @Test
    void shouldDefaultTimestamp() {
        ToolSharingMessage msg = ToolSharingMessage.builder()
                .messageId("msg-1")
                .eventType(ToolSharingMessage.SharingEventType.TOOL_UPDATED)
                .toolId("pub:tool-a")
                .name("Tool A")
                .publisher("pub")
                .build();

        assertNotNull(msg.getTimestamp());
    }

    @Test
    void shouldHaveAllEventTypes() {
        assertEquals(4, ToolSharingMessage.SharingEventType.values().length);
    }
}
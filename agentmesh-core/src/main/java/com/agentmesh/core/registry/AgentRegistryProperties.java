package com.agentmesh.core.registry;

import com.agentmesh.core.protocol.AgentCard;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "agentmesh.registry")
public class AgentRegistryProperties {

    private SelfConfig self = new SelfConfig();
    private List<String> peers = new ArrayList<>();

    @Data
    public static class SelfConfig {
        private String agentId;
        private String name;
        private String description;
        private String version = "1.0.0";
        private String url;

        public AgentCard toAgentCard() {
            return AgentCard.builder()
                    .agentId(agentId)
                    .name(name)
                    .description(description)
                    .version(version)
                    .url(url)
                    .skills(new ArrayList<>()) // skills 由 ToolRegistry 动态填充
                    .build();
        }
    }
}
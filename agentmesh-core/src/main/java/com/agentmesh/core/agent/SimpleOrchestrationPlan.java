package com.agentmesh.core.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * 编排计划的具体实现。
 * 支持 Builder 模式构建 Agent 拓扑。
 */
public class SimpleOrchestrationPlan implements OrchestrationPlan {

    private final List<AgentConfig> agents;
    private final ExecutionMode mode;
    private final String routingRule;

    private SimpleOrchestrationPlan(Builder builder) {
        this.agents = List.copyOf(builder.agents);
        this.mode = builder.mode;
        this.routingRule = builder.routingRule;
    }

    @Override
    public List<AgentConfig> getAgents() {
        return agents;
    }

    @Override
    public ExecutionMode getMode() {
        return mode;
    }

    @Override
    public String getRoutingRule() {
        return routingRule;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<AgentConfig> agents = new ArrayList<>();
        private ExecutionMode mode = ExecutionMode.SEQUENTIAL;
        private String routingRule;

        public Builder addAgent(AgentConfig config) {
            agents.add(config);
            return this;
        }

        public Builder mode(ExecutionMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder routingRule(String rule) {
            this.routingRule = rule;
            return this;
        }

        public SimpleOrchestrationPlan build() {
            return new SimpleOrchestrationPlan(this);
        }
    }
}
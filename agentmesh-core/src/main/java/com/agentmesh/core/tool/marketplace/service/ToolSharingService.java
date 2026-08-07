package com.agentmesh.core.tool.marketplace.service;

import com.agentmesh.core.tool.marketplace.model.ToolSharingMessage;
import com.agentmesh.core.tool.marketplace.repository.ToolRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 跨 Agent 工具共享服务。
 */
public interface ToolSharingService {

    /**
     * 广播工具发布事件。
     */
    Mono<String> publishTool(ToolSharingMessage message);

    /**
     * 广播工具更新事件。
     */
    Mono<String> updateTool(ToolSharingMessage message);

    /**
     * 广播工具下架事件。
     */
    Mono<String> deprecateTool(ToolSharingMessage message);

    /**
     * 订阅工具共享事件流。
     * @param agentId 本 Agent ID
     * @return 工具共享事件流
     */
    Flux<ToolSharingMessage> subscribe(String agentId);

    /**
     * 搜索市场中所有可用的工具（聚合本地 + 远程发现）。
     */
    ToolRepository.SearchResult searchGlobal(String keyword, int offset, int limit);
}

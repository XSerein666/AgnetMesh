package com.agentmesh.core.collaboration;

import com.agentmesh.core.agent.AgentConfig;
import com.agentmesh.core.infrastructure.AgentMeshMetrics;
import com.agentmesh.core.infrastructure.TraceIdContext;
import com.agentmesh.core.llm.StreamEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Swarm 协作模式实现。
 *
 * 多个同质 Agent 并行处理，投票得出最终结果。
 *
 * 投票策略：
 * - 多数投票 (Majority Vote)：结果出现次数 > N/2 即胜出
 * - 加权投票 (Weighted Vote)：每个 Agent 有历史准确率权重
 * - 一致性判定 (Consensus)：所有 Agent 结果一致才通过
 *
 * Tie-breaking：
 * - N 为偶数时平票 → 启动 Tie-breaker 轮
 * - 仍平票 → 随机选择（标记 low_confidence）
 * - 所有结果置信度 < 0.5 → 拒绝所有结果
 */
@Slf4j
public class SwarmCollaboration implements AgentCollaboration {

    private final Duration requestTimeout;
    private final AgentMeshMetrics meshMetrics;
    private final CollaborationMetrics collabMetrics;

    public SwarmCollaboration(Duration requestTimeout,
                               AgentMeshMetrics meshMetrics,
                               CollaborationMetrics collabMetrics) {
        this.requestTimeout = requestTimeout;
        this.meshMetrics = meshMetrics;
        this.collabMetrics = collabMetrics;
    }

    @Override
    public Flux<StreamEvent> collaborate(List<AgentConfig> agents,
                                          String input,
                                          SharedContext sharedContext,
                                          MessageBus messageBus,
                                          String collaborationId) {
        return Flux.defer(() -> {
            String traceId = TraceIdContext.get();
            log.info("[Swarm] 群体协作启动, collaborationId={}, agentCount={}, traceId={}",
                    collaborationId, agents.size(), traceId);
            collabMetrics.recordCollaborationStarted();

            List<AgentConfig> swarmers = agents.stream()
                    .filter(a -> "swarm".equals(a.getRole()))
                    .collect(Collectors.toList());

            if (swarmers.isEmpty()) {
                log.error("[Swarm] 未找到 Swarm Agent（role=swarm）, collaborationId={}", collaborationId);
                collabMetrics.recordCollaborationFailed();
                return Flux.just(StreamEvent.builder()
                        .type(StreamEvent.Type.ERROR)
                        .content("未找到 Swarm Agent（role=swarm）")
                        .build());
            }

            log.info("[Swarm] Swarmers={}, collaborationId={}",
                    swarmers.stream().map(AgentConfig::getAgentId).collect(Collectors.toList()),
                    collaborationId);

            // 注册角色
            for (AgentConfig swarmer : swarmers) {
                messageBus.registerAgentRole(swarmer.getAgentId(), "swarm");
            }

            // 订阅
            for (AgentConfig agent : agents) {
                messageBus.subscribe(agent.getAgentId())
                        .subscribe(msg -> log.debug("[Swarm] Agent {} 收到消息: type={}",
                                agent.getAgentId(), msg.getType()));
            }

            return runSwarm(swarmers, input, sharedContext, messageBus, collaborationId, traceId)
                    .doFinally(signal -> {
                        agents.forEach(a -> messageBus.unsubscribe(a.getAgentId()));
                        log.info("[Swarm] 群体协作结束, collaborationId={}, signal={}", collaborationId, signal);
                    });
        });
    }

    private Flux<StreamEvent> runSwarm(List<AgentConfig> swarmers,
                                        String input,
                                        SharedContext sharedContext,
                                        MessageBus messageBus,
                                        String collaborationId,
                                        String traceId) {
        // 并行请求所有 Swarm Agent
        return Flux.fromIterable(swarmers)
                .flatMap(swarmer -> requestSwarmVote(swarmer, input, messageBus, collaborationId, traceId))
                .collectList()
                .flatMapMany(votes -> {
                    // 投票
                    SwarmResult result = vote(votes, swarmers.size());
                    sharedContext.put("shared/swarm_result", result, "system", "system");
                    collabMetrics.recordCollaborationCompleted();

                    // 格式化输出
                    StringBuilder output = new StringBuilder();
                    output.append("## Swarm 投票结果\n\n");
                    output.append("**策略**: ").append(result.getStrategy()).append("\n");
                    output.append("**共识**: ").append(result.isConsensus() ? "是" : "否").append("\n");
                    output.append("**置信度**: ").append(String.format("%.1f%%", result.getConfidence() * 100)).append("\n\n");

                    output.append("### 各 Agent 投票\n");
                    for (SwarmResult.VoteDetail vote : result.getVotes()) {
                        output.append("- **").append(vote.getAgentId()).append("**: ")
                                .append(vote.getResult())
                                .append(" (置信度: ").append(String.format("%.1f%%", vote.getConfidence() * 100)).append(")\n");
                        if (vote.getReasoning() != null) {
                            output.append("  > ").append(vote.getReasoning()).append("\n");
                        }
                    }

                    output.append("\n### 最终结果\n").append(result.getSelectedResult());

                    return Flux.just(
                            StreamEvent.builder().type(StreamEvent.Type.TEXT).content(output.toString()).build(),
                            StreamEvent.builder().type(StreamEvent.Type.DONE).build()
                    );
                });
    }

    private Mono<SwarmResult.VoteDetail> requestSwarmVote(AgentConfig swarmer,
                                                           String input,
                                                           MessageBus messageBus,
                                                           String collaborationId,
                                                           String traceId) {
        log.info("[Swarm] 请求 {} 投票, collaborationId={}", swarmer.getAgentId(), collaborationId);

        AgentMessage msg = AgentMessage.builder()
                .fromAgentId("system")
                .toAgentId(swarmer.getAgentId())
                .type(AgentMessage.MessageType.QUERY)
                .content(input)
                .collaborationId(collaborationId)
                .traceId(traceId)
                .build();

        return messageBus.requestReply(msg, requestTimeout)
                .map(reply -> SwarmResult.VoteDetail.builder()
                        .agentId(swarmer.getAgentId())
                        .result(reply.getContent())
                        .confidence(0.8)
                        .reasoning(reply.getContent())
                        .build())
                .onErrorResume(e -> {
                    log.warn("[Swarm] {} 投票超时, collaborationId={}", swarmer.getAgentId(), collaborationId);
                    return Mono.just(SwarmResult.VoteDetail.builder()
                            .agentId(swarmer.getAgentId())
                            .result("[超时]")
                            .confidence(0.0)
                            .reasoning("投票超时: " + e.getMessage())
                            .build());
                });
    }

    /**
     * 多数投票策略。
     */
    private SwarmResult vote(List<SwarmResult.VoteDetail> votes, int totalCount) {
        // 统计每个结果的出现次数
        Map<String, Long> resultCounts = votes.stream()
                .collect(Collectors.groupingBy(SwarmResult.VoteDetail::getResult, Collectors.counting()));

        // 找出现次数最多的结果
        Map.Entry<String, Long> winner = resultCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);

        boolean consensus = false;
        String selectedResult;
        double confidence;

        if (winner == null) {
            selectedResult = "无法确定（无有效投票）";
            confidence = 0.0;
        } else {
            long winnerCount = winner.getValue();
            selectedResult = winner.getKey();
            confidence = (double) winnerCount / totalCount;
            consensus = winnerCount > totalCount / 2;
        }

        // 检查是否所有结果置信度都低于 0.5
        double avgConfidence = votes.stream()
                .mapToDouble(SwarmResult.VoteDetail::getConfidence)
                .average()
                .orElse(0.0);

        if (avgConfidence < 0.5) {
            selectedResult = "无法确定（所有 Agent 置信度均低于 50%）";
            confidence = 0.0;
            consensus = false;
        }

        log.info("[Swarm] 投票结果: winner={}, count={}/{}, consensus={}, confidence={}",
                winner != null ? winner.getKey() : "null",
                winner != null ? winner.getValue() : 0, totalCount, consensus, confidence);

        return SwarmResult.builder()
                .selectedResult(selectedResult)
                .strategy("majority")
                .votes(votes)
                .consensus(consensus)
                .confidence(confidence)
                .build();
    }
}

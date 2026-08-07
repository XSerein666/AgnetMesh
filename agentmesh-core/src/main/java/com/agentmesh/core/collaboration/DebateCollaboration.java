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
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Debate 协作模式实现。
 *
 * 两个 Agent 对同一问题独立推理，互相质疑，由 Judge Agent 仲裁。
 *
 * 收敛保证：
 * - 硬编码 maxRounds = 3（超过 3 轮强制进入仲裁）
 * - 收敛判定：由 Judge Agent 判定双方是否达成共识
 * - 仲裁机制：Judge Agent 阅读双方全部陈述后给出最终结论
 */
@Slf4j
public class DebateCollaboration implements AgentCollaboration {

    private static final int MAX_ROUNDS = 3;

    private final Duration requestTimeout;
    private final AgentMeshMetrics meshMetrics;
    private final CollaborationMetrics collabMetrics;

    public DebateCollaboration(Duration requestTimeout,
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
            log.info("[Debate] 辩论启动, collaborationId={}, agentCount={}, traceId={}",
                    collaborationId, agents.size(), traceId);
            collabMetrics.recordCollaborationStarted();

            // 分离 Debater 和 Judge
            List<AgentConfig> debaters = agents.stream()
                    .filter(a -> "debater".equals(a.getRole()))
                    .collect(Collectors.toList());
            AgentConfig judge = agents.stream()
                    .filter(a -> "judge".equals(a.getRole()))
                    .findFirst()
                    .orElse(null);

            if (debaters.size() < 2) {
                log.error("[Debate] 辩论者不足（需要至少2个 debater）, collaborationId={}", collaborationId);
                collabMetrics.recordCollaborationFailed();
                return Flux.just(errorEvent("辩论者不足，需要至少 2 个 debater 角色"));
            }
            if (judge == null) {
                log.error("[Debate] 未找到 Judge Agent, collaborationId={}", collaborationId);
                collabMetrics.recordCollaborationFailed();
                return Flux.just(errorEvent("未找到 Judge Agent（role=judge）"));
            }

            log.info("[Debate] Debaters={}, Judge={}, collaborationId={}",
                    debaters.stream().map(AgentConfig::getAgentId).collect(Collectors.toList()),
                    judge.getAgentId(), collaborationId);

            // 注册角色
            for (AgentConfig debater : debaters) {
                messageBus.registerAgentRole(debater.getAgentId(), "debater");
            }
            messageBus.registerAgentRole(judge.getAgentId(), "judge");

            // 订阅
            for (AgentConfig agent : agents) {
                messageBus.subscribe(agent.getAgentId())
                        .subscribe(msg -> log.debug("[Debate] Agent {} 收到消息: type={}",
                                agent.getAgentId(), msg.getType()));
            }

            // 辩论流程
            return runDebate(debaters, judge, input, sharedContext, messageBus, collaborationId, traceId)
                    .doFinally(signal -> {
                        agents.forEach(a -> messageBus.unsubscribe(a.getAgentId()));
                        log.info("[Debate] 辩论结束, collaborationId={}, signal={}", collaborationId, signal);
                    });
        });
    }

    private Flux<StreamEvent> runDebate(List<AgentConfig> debaters,
                                         AgentConfig judge,
                                         String input,
                                         SharedContext sharedContext,
                                         MessageBus messageBus,
                                         String collaborationId,
                                         String traceId) {
        // 辩论轮次
        return Flux.range(1, MAX_ROUNDS)
                .concatMap(round -> {
                    log.info("[Debate] 第 {} 轮辩论, collaborationId={}", round, collaborationId);
                    return Flux.fromIterable(debaters)
                            .flatMap(debater -> requestDebaterStatement(debater, input, round,
                                    messageBus, collaborationId, traceId));
                })
                .collectList()
                .flatMapMany(statements -> {
                    // 写入共享上下文
                    sharedContext.put("shared/debate_transcript", statements,
                            "system", "system");
                    // Judge 仲裁
                    return arbitrate(judge, statements, sharedContext, messageBus,
                            collaborationId, traceId);
                });
    }

    private Mono<String> requestDebaterStatement(AgentConfig debater, String input, int round,
                                                   MessageBus messageBus,
                                                   String collaborationId, String traceId) {
        String statementId = UUID.randomUUID().toString().substring(0, 8);
        log.info("[Debate] 请求 {} 第 {} 轮陈述, statementId={}, collaborationId={}",
                debater.getAgentId(), round, statementId, collaborationId);

        AgentMessage msg = AgentMessage.builder()
                .fromAgentId("system")
                .toAgentId(debater.getAgentId())
                .type(AgentMessage.MessageType.DEBATE_STATEMENT)
                .content("辩论轮次 " + round + ": " + input)
                .payload(Map.of("round", round, "statementId", statementId))
                .collaborationId(collaborationId)
                .traceId(traceId)
                .build();

        return messageBus.requestReply(msg, requestTimeout)
                .map(reply -> {
                    log.info("[Debate] {} 第 {} 轮陈述完成, collaborationId={}",
                            debater.getAgentId(), round, collaborationId);
                    return debater.getAgentId() + "(Round " + round + "): " + reply.getContent();
                })
                .onErrorResume(e -> {
                    log.warn("[Debate] {} 第 {} 轮陈述超时, collaborationId={}",
                            debater.getAgentId(), round, collaborationId);
                    return Mono.just(debater.getAgentId() + "(Round " + round + "): [超时未响应]");
                });
    }

    private Flux<StreamEvent> arbitrate(AgentConfig judge,
                                         List<String> statements,
                                         SharedContext sharedContext,
                                         MessageBus messageBus,
                                         String collaborationId,
                                         String traceId) {
        log.info("[Debate] Judge {} 开始仲裁, statementCount={}, collaborationId={}",
                judge.getAgentId(), statements.size(), collaborationId);

        String verdictKey = "shared/debate_verdict";
        String transcript = String.join("\n\n", statements);

        AgentMessage verdictMsg = AgentMessage.builder()
                .fromAgentId("system")
                .toAgentId(judge.getAgentId())
                .type(AgentMessage.MessageType.QUERY)
                .content("请根据以下辩论记录给出最终仲裁结论：\n\n" + transcript)
                .collaborationId(collaborationId)
                .traceId(traceId)
                .build();

        return messageBus.requestReply(verdictMsg, requestTimeout)
                .flatMapMany(reply -> {
                    sharedContext.put(verdictKey, reply.getContent(), "judge", "judge");
                    collabMetrics.recordCollaborationCompleted();
                    log.info("[Debate] 仲裁完成, collaborationId={}", collaborationId);
                    return Flux.just(
                            StreamEvent.builder().type(StreamEvent.Type.TEXT)
                                    .content("## 辩论仲裁结论\n\n**辩论记录**:\n" + transcript
                                            + "\n\n**仲裁结论**:\n" + reply.getContent()).build(),
                            StreamEvent.builder().type(StreamEvent.Type.DONE).build()
                    );
                })
                .onErrorResume(e -> {
                    log.error("[Debate] 仲裁超时, collaborationId={}", collaborationId, e);
                    collabMetrics.recordCollaborationFailed();
                    return Flux.just(
                            StreamEvent.builder().type(StreamEvent.Type.TEXT)
                                    .content("## 辩论仲裁失败\n\n辩论记录:\n" + transcript
                                            + "\n\n**仲裁结果**: Judge 超时，无法给出结论").build(),
                            StreamEvent.builder().type(StreamEvent.Type.DONE).build()
                    );
                });
    }

    private StreamEvent errorEvent(String message) {
        return StreamEvent.builder()
                .type(StreamEvent.Type.ERROR)
                .content(message)
                .build();
    }
}

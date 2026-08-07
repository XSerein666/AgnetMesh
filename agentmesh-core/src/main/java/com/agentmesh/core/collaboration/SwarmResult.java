package com.agentmesh.core.collaboration;

import lombok.Builder;
import lombok.Data;
import java.util.List;

/**
 * Swarm 投票结果。
 * 群体投票模式的最终输出，包含投票策略、详情和置信度。
 */
@Data
@Builder
public class SwarmResult {

    /** 最终选择的结果 */
    private String selectedResult;

    /** 投票策略 */
    private String strategy; // "majority" | "weighted" | "consensus"

    /** 投票详情 */
    private List<VoteDetail> votes;

    /** 是否达成共识 */
    private boolean consensus;

    /** 置信度（0-1） */
    private double confidence;

    @Data
    @Builder
    public static class VoteDetail {
        private String agentId;
        private String result;
        private double confidence;
        private String reasoning;
    }
}

package com.agentmesh.core.routing;

import com.agentmesh.core.agent.AgentConfig;
import lombok.Builder;
import lombok.Data;

/**
 * 路由结果：Agent + score + 置信度。
 *
 * 字段区分：
 * - score：阶段1粗筛的原始打分（整数，无上界），仅用于阶段1排序
 * - confidence：归一化置信度 [0.0, 1.0]，阶段2 LLM 填充，阶段1 时归一化到 0-1
 * - reason：路由理由（LLM 路由时由模型输出，关键词路由时保留 null）
 */
@Data
@Builder
public class RankedAgent {
    private AgentConfig agent;
    /** 阶段1粗筛原始打分，0 表示未参与粗筛 */
    private int score;
    /** 置信度 [0.0, 1.0]，clamp 保证不出界 */
    private double confidence;
    /** 路由理由（LLM 输出） */
    private String reason;
}

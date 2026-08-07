package com.agentmesh.core.agent;

/**
 * 执行模式
 */
public enum ExecutionMode {
    SEQUENTIAL,   // 顺序执行
    PARALLEL,     // 并行执行
    CONDITIONAL,  // 条件路由
    SUPERVISED,   // Supervisor-Worker 协作（Phase 2）
    DEBATE,       // 辩论模式（Phase 3）
    SWARM         // 群体投票模式（Phase 3）
}

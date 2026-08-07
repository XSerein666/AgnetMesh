-- ============================================
-- Jewel-A2A 数据库初始化脚本
-- Spring Boot 启动时自动执行
-- ============================================

-- 创建 pgvector 扩展（用于后续 RAG 知识库）
CREATE EXTENSION IF NOT EXISTS vector;

-- 任务表
CREATE TABLE IF NOT EXISTS a2a_task (
    id              BIGSERIAL PRIMARY KEY,
    task_id         VARCHAR(64)  NOT NULL UNIQUE,
    skill_id        VARCHAR(64)  NOT NULL,
    input           JSONB        NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    output          JSONB,
    error_message   TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_task_status ON a2a_task(status);
CREATE INDEX IF NOT EXISTS idx_task_created ON a2a_task(created_at);

-- 知识库表（pgvector，RAG 工艺知识库）
CREATE TABLE IF NOT EXISTS craft_knowledge (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    content     TEXT         NOT NULL,
    embedding   VECTOR(1024),
    category    VARCHAR(64),
    metadata    JSONB,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_craft_embedding ON craft_knowledge USING ivfflat (embedding vector_cosine_ops);

-- 会话表（多轮对话历史）
CREATE TABLE IF NOT EXISTS conversation (
    id          BIGSERIAL PRIMARY KEY,
    session_id  VARCHAR(64) NOT NULL UNIQUE,
    messages    JSONB NOT NULL DEFAULT '[]',
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_conv_session ON conversation(session_id);
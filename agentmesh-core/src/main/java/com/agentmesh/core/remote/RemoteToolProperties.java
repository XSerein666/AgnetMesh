package com.agentmesh.core.remote;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "agentmesh.remote")
public class RemoteToolProperties {

    /** 远程调用总超时（默认 15 秒，同步轮询期间阻塞 Tomcat 线程） */
    private Duration timeout = Duration.ofSeconds(15);

    /** 轮询任务状态间隔（默认 1 秒） */
    private Duration pollInterval = Duration.ofSeconds(1);

    /** 流式调用超时（默认 60 秒，防止远程 Agent 卡住时永久占住 SSE 连接） */
    private Duration streamTimeout = Duration.ofSeconds(60);
}

package com.agentmesh.core.tool.marketplace.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 工具市场 OpenAPI 配置。
 * 提供 Swagger UI 和 OpenAPI 3.0 文档支持。
 */
@Configuration
@ConditionalOnExpression(
    "${spring.agentmesh.marketplace.enabled:true} && ${spring.agentmesh.marketplace.phase4.enabled:false}")
public class MarketplaceOpenApiConfig {

    @Bean
    public OpenAPI marketplaceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AgentMesh 工具市场 API")
                        .description("""
                                AgentMesh 工具市场 REST API 文档。
                                
                                ## 功能模块
                                - **公开接口**：工具浏览、搜索、排行榜、详情查看
                                - **Publisher 接口**：工具安装、卸载、升级、评价
                                - **Admin 接口**：工具审核、下架、分类管理
                                
                                ## 认证
                                API 通过 `X-API-Key` 头进行认证，不同角色拥有不同权限级别。
                                """)
                        .version("1.0.1")
                        .contact(new Contact()
                                .name("XSerein666")
                                .url("https://github.com/XSerein666"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("本地开发环境"),
                        new Server()
                                .url("https://agentmesh.example.com")
                                .description("生产环境")
                ))
                .tags(List.of(
                        new Tag().name("公开接口").description("无需认证的公开 API"),
                        new Tag().name("Publisher 接口").description("工具发布者使用的 API"),
                        new Tag().name("Admin 接口").description("管理员专用 API")
                ));
    }
}

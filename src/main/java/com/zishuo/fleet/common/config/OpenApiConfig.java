package com.zishuo.fleet.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 文档配置(替代过时的 Swagger 2)。
 *
 * <h2>访问方式</h2>
 * 应用启动后:
 * <ul>
 *   <li>交互式 UI: {@code http://localhost:8080/swagger-ui.html}</li>
 *   <li>JSON 规范: {@code http://localhost:8080/v3/api-docs}</li>
 * </ul>
 *
 * <h2>JWT 集成</h2>
 * 这里声明了 "bearerAuth" 安全方案,Swagger UI 会显示一个"Authorize"按钮,
 * 点击后输入 JWT token,后续所有"Try it out"会自动加上 Authorization header。
 *
 * <h2>真实接口扫描</h2>
 * springdoc-openapi 会自动扫描所有 @RestController + @Operation 注解的方法,
 * 我们这里只配置元数据,不需要手动列出每个接口。
 */

//配置出一个浏览器可访问的 API 文档/调试页面,开发期超有用, OpenAPI 3 自动生成的 API 文档
@Configuration // 标识为 Spring 配置类,启动时被扫描并执行 @Bean 方法
public class OpenApiConfig {

    /** 安全方案的名字,在多个地方引用,提取为常量 */
    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    /**
     * 定义 OpenAPI 文档的元信息和安全方案。
     * 返回值会被 springdoc 用作生成文档的根对象。
     */
    @Bean
    public OpenAPI fleetOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cloud Robotics Fleet Management Platform API")
                        .description("Multi-tenant backend for managing cloud-connected robots.")
                        .version("v0.0.1"))
                // 全局应用 bearerAuth 安全要求,意味着默认所有接口都需要 token
                // (公开接口需要在 Controller 上加 @SecurityRequirements 覆盖)
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste a JWT obtained from /api/v1/auth/login")));
    }
}

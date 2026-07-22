package com.zishuo.fleet.health;

import com.zishuo.fleet.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * 健康检查接口 - 提供给负载均衡器、监控系统的存活探测端点。
 *
 * <h2>为什么需要</h2>
 * 生产环境的负载均衡器(如 AWS ALB)会定期请求这个端点,如果连续失败就把这个实例从流量池移除。
 *
 * <h2>为什么要公开访问(无需认证)</h2>
 * 监控系统不持有用户凭据;让负载均衡器持有 JWT 也不合理。
 * 通过类上的 @SecurityRequirements(空)覆盖 OpenApiConfig 里全局的 bearerAuth 要求。
 *
 * <h2>对比 Spring Actuator 的 /actuator/health</h2>
 * Actuator 自带的 /actuator/health 也能用,但它会反映数据库连接等状态(可能误报"不健康"导致整个实例下线)。
 * 这个端点只表示"应用进程还活着",更适合做基础存活探测。
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Liveness probe") // OpenAPI 分组标签
@SecurityRequirements // 空注解 = 覆盖类级别的"需要 JWT"要求,使本接口公开.它是给机器（监控、负载均衡器）用的，不是给人（前端用户）用的。这些“机器客户”没有、也不应该有业务身份。
public class HealthController {

    @GetMapping
    @Operation(summary = "Liveness probe — always returns ok if the app is up")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of(
                "application", "robotics-fleet-platform",
                "status", "UP",
                "timestamp", Instant.now().toString()));
    }
}

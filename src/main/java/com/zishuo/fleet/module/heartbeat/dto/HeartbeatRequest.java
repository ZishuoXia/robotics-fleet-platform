package com.zishuo.fleet.module.heartbeat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * 设备心跳上报请求 - HTTP POST /api/v1/devices/{id}/heartbeat 的请求体。
 *
 * <h2>字段说明</h2>
 * <ul>
 *   <li>batteryLevel:必填,0-100 之间</li>
 *   <li>location:可选,设备 GPS 位置</li>
 *   <li>metrics:可选,扩展指标 Map(温度、CPU、内存等)</li>
 * </ul>
 *
 * <h2>为什么没有 timestamp?</h2>
 * 服务端收到请求时用 Instant.now() 作为时间戳。
 * 不让客户端传时间戳的原因:防止设备时钟不准或恶意伪造历史时间。
 */
public record HeartbeatRequest(
        @NotNull(message = "batteryLevel is required")
        @Min(value = 0, message = "batteryLevel must be >= 0")
        @Max(value = 100, message = "batteryLevel must be <= 100")
        @Schema(description = "Battery percentage 0-100", example = "85")
        Integer batteryLevel,

        @Schema(description = "Location in 'lat,lon' format", example = "37.7749,-122.4194")
        String location,

        @Schema(description = "Additional metrics (CPU, memory, temperature, etc.)")
        Map<String, Object> metrics
) {
}

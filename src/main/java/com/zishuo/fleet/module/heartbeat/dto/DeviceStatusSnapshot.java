package com.zishuo.fleet.module.heartbeat.dto;

import com.zishuo.fleet.module.device.domain.DeviceStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 设备实时状态快照 - 用于 HTTP 查询接口的响应。
 *
 * <h2>数据来源</h2>
 * <ul>
 *   <li>基本信息(id, serialNumber, name)从 Postgres</li>
 *   <li>实时状态(status, lastSeenAt, battery, location)从 Redis</li>
 * </ul>
 * Service 层合并两个数据源,返回这个 DTO 给前端。
 *
 * <h2>为什么不直接复用 DeviceResponse?</h2>
 * DeviceResponse 是设备的"配置信息",这个是"运行时状态",
 * 关注点不同,分开 DTO 让 API 语义更清晰。
 */
@Schema(description = "Real-time snapshot of a device")
public record DeviceStatusSnapshot(
        Long deviceId,
        String serialNumber,
        String name,
        DeviceStatus status,
        Instant lastSeenAt,
        Integer batteryLevel,
        String location
) {
}

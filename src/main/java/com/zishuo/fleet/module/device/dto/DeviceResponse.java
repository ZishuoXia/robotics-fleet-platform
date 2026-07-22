package com.zishuo.fleet.module.device.dto;

import com.zishuo.fleet.module.device.domain.Device;
import com.zishuo.fleet.module.device.domain.DeviceStatus;

import java.time.Instant;

/**
 * 设备响应 DTO。
 *
 * <p>不直接返回 Device 实体的原因:
 * <ul>
 *   <li>实体的 createdBy/updatedBy 字段是内部审计信息,不必暴露</li>
 *   <li>未来给实体加内部字段时不会污染 API 契约</li>
 *   <li>序列化更可控(可以选择性输出字段)</li>
 * </ul>
 */
public record DeviceResponse(
        Long id,
        Long tenantId,
        String serialNumber,
        String name,
        String model,
        String firmwareVersion,
        DeviceStatus status,
        Instant lastSeenAt,
        Instant createdAt,
        Instant updatedAt
) {
    /** Entity → DTO 转换 */
    public static DeviceResponse from(Device d) {
        return new DeviceResponse(
                d.getId(),
                d.getTenantId(),
                d.getSerialNumber(),
                d.getName(),
                d.getModel(),
                d.getFirmwareVersion(),
                d.getStatus(),
                d.getLastSeenAt(),
                d.getCreatedAt(),
                d.getUpdatedAt());
    }
}

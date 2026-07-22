package com.zishuo.fleet.module.heartbeat.dto;

import com.zishuo.fleet.module.device.domain.DeviceStatus;

import java.time.Instant;

/**
 * WebSocket 推送给前端的状态变更事件。
 *
 * <h2>触发场景</h2>
 * <ul>
 *   <li>设备首次上线(status: PROVISIONED/OFFLINE → ONLINE)</li>
 *   <li>定时任务检测到离线(status: ONLINE → OFFLINE)</li>
 *   <li>(可选)每次心跳都推送,让前端实时看到电量变化</li>
 * </ul>
 *
 * <h2>设计考虑</h2>
 * 字段相对精简,只包含前端"实时刷新设备卡片"需要的信息。
 * 不包含 tenantId(因为 WebSocket 主题已经按 tenant 隔离,无需重复)。
 */
public record DeviceStatusUpdate(
        Long deviceId,
        String serialNumber,
        DeviceStatus status,
        Instant timestamp,
        Integer batteryLevel,
        String location
) {
}

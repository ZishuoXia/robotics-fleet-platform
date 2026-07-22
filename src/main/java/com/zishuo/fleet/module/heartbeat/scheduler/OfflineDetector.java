package com.zishuo.fleet.module.heartbeat.scheduler;

import com.zishuo.fleet.module.device.domain.Device;
import com.zishuo.fleet.module.device.domain.DeviceStatus;
import com.zishuo.fleet.module.device.repository.DeviceRepository;
import com.zishuo.fleet.module.heartbeat.dto.DeviceStatusUpdate;
import com.zishuo.fleet.module.heartbeat.service.DeviceStatusService;
import com.zishuo.fleet.module.heartbeat.ws.DeviceStatusBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 离线设备检测定时任务。
 *
 * <h2>为什么需要这个?</h2>
 * Redis 的状态有 90 秒 TTL,超时自动消失。但 Postgres 的 status 字段
 * 不会自动变成 OFFLINE,需要主动检测。
 *
 * <h2>工作流程</h2>
 * <ol>
 *   <li>每 10 秒触发一次(配置项 app.heartbeat.offline-scan-interval-ms)</li>
 *   <li>查 Postgres:找 status=ONLINE 且 last_seen_at 早于 90 秒前的设备</li>
 *   <li>批量 UPDATE 这些设备 status=OFFLINE</li>
 *   <li>对每个变离线的设备:
 *     <ul>
 *       <li>清理 Redis(即使已过期也兜底删一次)</li>
 *       <li>通过 WebSocket 推送状态变更</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <h2>分布式部署的考量</h2>
 * 如果应用部署多副本,所有副本都会跑这个定时任务,可能重复扫描。
 * 解决方案(后续可以加):
 * <ul>
 *   <li>用 Redis 分布式锁(SETNX)保证同一时刻只有一个副本执行</li>
 *   <li>或用 Spring Cloud Task / Quartz Cluster</li>
 * </ul>
 * 目前单实例运行不需要,但要在文档里说明。
 *
 * <h2>{@code @Scheduled} 触发模式</h2>
 * <ul>
 *   <li>fixedRate:固定间隔触发(不管上次执行多久)</li>
 *   <li>fixedDelay:上次完成 N 毫秒后再触发</li>
 *   <li>cron:cron 表达式</li>
 * </ul>
 * 用 fixedDelay 更安全,避免上次任务还没结束就启动下一次。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfflineDetector {

    private final DeviceRepository deviceRepository;
    private final DeviceStatusService statusService;
    private final DeviceStatusBroadcaster broadcaster;

    @Value("${app.heartbeat.offline-threshold-seconds:90}")
    private long offlineThresholdSeconds;

    @Scheduled(fixedDelayString = "${app.heartbeat.offline-scan-interval-ms:10000}")
    @Transactional
    public void detectOfflineDevices() {
        Instant threshold = Instant.now().minusSeconds(offlineThresholdSeconds);

        // 1. 查超时的在线设备
        List<Device> staleDevices = deviceRepository.findStaleOnlineDevices(threshold);
        if (staleDevices.isEmpty()) {
            return;
        }

        log.info("Detected {} stale online devices (threshold: {}s)",
                staleDevices.size(), offlineThresholdSeconds);

        // 2. 批量更新 Postgres status -> OFFLINE
        List<Long> deviceIds = staleDevices.stream()
                .map(Device::getId)
                .collect(Collectors.toList());
        deviceRepository.markOffline(deviceIds);

        // 3. 清理 Redis + 推送 WebSocket
        for (Device device : staleDevices) {
            statusService.markOffline(device.getTenantId(), device.getId());

            DeviceStatusUpdate update = new DeviceStatusUpdate(
                    device.getId(),
                    device.getSerialNumber(),
                    DeviceStatus.OFFLINE,
                    Instant.now(),
                    device.getBatteryLevel(),
                    device.getLastKnownLocation());
            broadcaster.broadcast(device.getTenantId(), update);
        }
    }
}

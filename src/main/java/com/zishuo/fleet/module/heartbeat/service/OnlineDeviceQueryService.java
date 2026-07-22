package com.zishuo.fleet.module.heartbeat.service;

import com.zishuo.fleet.module.device.domain.Device;
import com.zishuo.fleet.module.device.domain.DeviceStatus;
import com.zishuo.fleet.module.device.repository.DeviceRepository;
import com.zishuo.fleet.module.heartbeat.dto.DeviceStatusSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 在线设备查询服务 - 合并 Redis 实时数据 + Postgres 基本信息。
 *
 * <h2>为什么不直接查 Postgres?</h2>
 * <ul>
 *   <li>Postgres 的 last_seen_at 字段是延迟更新的(每次心跳都 UPDATE,
 *       但要避免每条都同步,实际可能有几秒延迟)</li>
 *   <li>Redis 是实时的,毫秒级</li>
 *   <li>查询频率高(前端 dashboard 反复刷新),Redis 减压数据库</li>
 * </ul>
 *
 * <h2>数据合并策略</h2>
 * <ol>
 *   <li>先从 Redis 取所有在线设备 ID(O(1))</li>
 *   <li>用 ID 集合从 Postgres 批量查 Device 基本信息(name, serialNumber 等)</li>
 *   <li>对每个 device,从 Redis 取实时元数据(battery, location)</li>
 *   <li>合并成 DeviceStatusSnapshot 列表返回</li>
 * </ol>
 *
 * <h2>错误处理</h2>
 * 如果 Redis 里有但 Postgres 里没有(数据不一致),跳过这个 device 并 warn。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineDeviceQueryService {

    private final DeviceRepository deviceRepository;
    private final DeviceStatusService statusService;

    /**
     * 查询某租户当前所有在线设备。
     *
     * @param tenantId 租户 ID
     * @return 在线设备快照列表
     */
    @Transactional(readOnly = true)
    public List<DeviceStatusSnapshot> listOnlineDevices(Long tenantId) {
        // 1. Redis 取在线设备 ID
        Set<Long> onlineIds = statusService.getOnlineDeviceIds(tenantId);
        if (onlineIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. Postgres 批量查基本信息(只查同租户,多租户隔离)
        List<Device> devices = deviceRepository.findAllById(onlineIds).stream()
                .filter(d -> d.getTenantId().equals(tenantId))   // 防御性过滤
                .collect(Collectors.toList());

        // 3. Redis 取每个设备的实时元数据
        Map<Long, DeviceStatusSnapshot> realtimeMap = new HashMap<>();
        for (Device d : devices) {
            DeviceStatusSnapshot realtime = statusService.getRealtimeStatus(tenantId, d.getId());
            realtimeMap.put(d.getId(), realtime);
        }

        // 4. 合并 Postgres 基本信息 + Redis 实时数据
        return devices.stream()
                .map(d -> {
                    DeviceStatusSnapshot rt = realtimeMap.get(d.getId());
                    DeviceStatus status = (rt != null && rt.status() != null)
                            ? rt.status() : DeviceStatus.OFFLINE;
                    return new DeviceStatusSnapshot(
                            d.getId(),
                            d.getSerialNumber(),
                            d.getName(),
                            status,
                            rt != null ? rt.lastSeenAt() : d.getLastSeenAt(),
                            rt != null ? rt.batteryLevel() : d.getBatteryLevel(),
                            rt != null ? rt.location() : d.getLastKnownLocation()
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * 查询单个设备的实时状态(合并 Redis + Postgres)。
     */
    @Transactional(readOnly = true)
    public DeviceStatusSnapshot getDeviceStatus(Long tenantId, Long deviceId) {
        Device device = deviceRepository.findByIdAndTenantId(deviceId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Device " + deviceId + " not found in tenant " + tenantId));

        DeviceStatusSnapshot realtime = statusService.getRealtimeStatus(tenantId, deviceId);

        return new DeviceStatusSnapshot(
                device.getId(),
                device.getSerialNumber(),
                device.getName(),
                realtime != null && realtime.status() != null
                        ? realtime.status() : device.getStatus(),
                realtime != null ? realtime.lastSeenAt() : device.getLastSeenAt(),
                realtime != null ? realtime.batteryLevel() : device.getBatteryLevel(),
                realtime != null ? realtime.location() : device.getLastKnownLocation()
        );
    }
}

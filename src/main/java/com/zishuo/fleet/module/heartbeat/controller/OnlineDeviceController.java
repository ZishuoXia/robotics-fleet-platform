package com.zishuo.fleet.module.heartbeat.controller;

import com.zishuo.fleet.common.response.ApiResponse;
import com.zishuo.fleet.module.heartbeat.dto.DeviceStatusSnapshot;
import com.zishuo.fleet.module.heartbeat.service.OnlineDeviceQueryService;
import com.zishuo.fleet.security.service.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 实时设备状态查询 Controller。
 *
 * <h2>路径设计</h2>
 * <ul>
 *   <li>GET /api/v1/devices/online - 列出当前在线设备(走 Redis,毫秒级)</li>
 *   <li>GET /api/v1/devices/{id}/status - 单设备实时状态</li>
 * </ul>
 *
 * <h2>vs. M1 的 GET /api/v1/devices</h2>
 * <ul>
 *   <li>M1 接口:从 Postgres 查,返回完整设备列表,包含 PROVISIONED/OFFLINE/ONLINE 等所有状态</li>
 *   <li>本接口:从 Redis 优先查,只返回在线设备,毫秒级响应,适合 dashboard 高频刷新</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Tag(name = "Device Status", description = "Real-time device status queries (Redis-backed)")
public class OnlineDeviceController {

    private final OnlineDeviceQueryService queryService;
    private final CurrentUserService currentUserService;

    @GetMapping("/online")
    @PreAuthorize("hasAuthority('device:read')")
    @Operation(summary = "List currently online devices (Redis-backed, <10ms)")
    public ApiResponse<List<DeviceStatusSnapshot>> listOnline() {
        Long tenantId = currentUserService.getCurrentTenantId();
        return ApiResponse.ok(queryService.listOnlineDevices(tenantId));
    }

    @GetMapping("/{id}/status")
    @PreAuthorize("hasAuthority('device:read')")
    @Operation(summary = "Get real-time status of a single device")
    public ApiResponse<DeviceStatusSnapshot> getStatus(@PathVariable Long id) {
        Long tenantId = currentUserService.getCurrentTenantId();
        return ApiResponse.ok(queryService.getDeviceStatus(tenantId, id));
    }
}

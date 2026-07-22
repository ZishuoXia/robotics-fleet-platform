package com.zishuo.fleet.module.heartbeat.controller;

import com.zishuo.fleet.common.response.ApiResponse;
import com.zishuo.fleet.module.device.domain.Device;
import com.zishuo.fleet.module.device.repository.DeviceRepository;
import com.zishuo.fleet.module.heartbeat.domain.HeartbeatEvent;
import com.zishuo.fleet.module.heartbeat.dto.HeartbeatRequest;
import com.zishuo.fleet.module.heartbeat.producer.HeartbeatProducer;
import com.zishuo.fleet.security.service.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * 心跳上报 Controller - 设备调用此接口报告自己的状态。
 *
 * <h2>异步处理流程</h2>
 * <pre>
 * 设备 ──POST heartbeat──> Controller ──> Kafka ──> Consumer ──> Redis/PG/WS
 *                            ↓
 *                       立即返回 202
 * </pre>
 * Controller 不做实际业务处理,只把请求转成 Kafka 消息,立即返回 202 Accepted。
 * 真正的处理在 Consumer 异步进行。这样:
 * <ul>
 *   <li>响应快(<10ms),设备不用等</li>
 *   <li>能扛突发流量(Kafka 当缓冲池)</li>
 *   <li>处理逻辑出错不影响心跳接收</li>
 * </ul>
 *
 * <h2>权限</h2>
 * 设备需要持有 device:write 权限才能上报心跳。
 * 实际生产中可能给设备单独签 device-only 的 JWT,权限更细。
 *
 * <h2>多租户隔离</h2>
 * 路径 /devices/{id}/heartbeat 中的 id 是 deviceId,
 * 必须先校验"这台设备属于调用者的租户",否则可以伪造他人租户的设备心跳。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Tag(name = "Heartbeat", description = "Device heartbeat reporting")
public class HeartbeatController {

    private final HeartbeatProducer producer;
    private final DeviceRepository deviceRepository;
    private final CurrentUserService currentUserService;

    /**
     * 上报心跳。设备每 30 秒调用一次。
     *
     * @return 202 Accepted(异步处理语义,不返回业务结果)
     */
    @PostMapping("/{deviceId}/heartbeat")
    @PreAuthorize("hasAuthority('device:write')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Report a device heartbeat (async, returns 202)")
    public ApiResponse<Void> heartbeat(@PathVariable Long deviceId,
                                       @Valid @RequestBody HeartbeatRequest request) {
        Long tenantId = currentUserService.getCurrentTenantId();

        // 校验设备存在且属于当前租户(防止伪造他人租户的心跳)
        Device device = deviceRepository.findByIdAndTenantId(deviceId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Device " + deviceId + " not found in tenant " + tenantId));

        HeartbeatEvent event = new HeartbeatEvent(
                tenantId,
                deviceId,
                device.getSerialNumber(),
                Instant.now(),
                request.batteryLevel(),
                request.location(),
                request.metrics()
        );

        producer.send(event);
        return ApiResponse.ok();
    }
}

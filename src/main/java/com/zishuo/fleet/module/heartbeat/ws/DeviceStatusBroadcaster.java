package com.zishuo.fleet.module.heartbeat.ws;

import com.zishuo.fleet.module.heartbeat.dto.DeviceStatusUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 设备状态变更广播器 - 通过 WebSocket 推送到所有订阅者。
 *
 * <h2>主题命名规则</h2>
 * <pre>
 * /topic/tenant/{tenantId}/devices
 * </pre>
 * 客户端订阅自己租户的主题,服务端按租户推送,实现多租户隔离。
 *
 * <h2>消息流</h2>
 * <pre>
 * HeartbeatConsumer 收到 Kafka 消息
 *      ↓
 * DeviceStatusService 更新 Redis
 *      ↓
 * DeviceStatusBroadcaster.broadcast(...)
 *      ↓
 * SimpMessagingTemplate.convertAndSend("/topic/tenant/1/devices", update)
 *      ↓
 * Spring Message Broker 转发给所有订阅 /topic/tenant/1/devices 的客户端
 * </pre>
 *
 * <h2>SimpMessagingTemplate vs SimpMessageSendingOperations</h2>
 * 都是 STOMP 的发送 API,前者是默认实现。Spring Boot 自动配置已创建好这个 bean,
 * 我们直接注入使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceStatusBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 推送状态变更给某租户的所有 WebSocket 订阅者。
     */
    public void broadcast(Long tenantId, DeviceStatusUpdate update) {
        String destination = "/topic/tenant/" + tenantId + "/devices";
        messagingTemplate.convertAndSend(destination, update);
        log.debug("Broadcast status update to {}: device={} status={}",
                destination, update.deviceId(), update.status());
    }
}

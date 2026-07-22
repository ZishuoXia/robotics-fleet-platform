package com.zishuo.fleet.module.heartbeat.consumer;

import com.zishuo.fleet.infrastructure.kafka.KafkaTopics;
import com.zishuo.fleet.module.device.domain.DeviceStatus;
import com.zishuo.fleet.module.device.repository.DeviceRepository;
import com.zishuo.fleet.module.heartbeat.domain.HeartbeatEvent;
import com.zishuo.fleet.module.heartbeat.dto.DeviceStatusUpdate;
import com.zishuo.fleet.module.heartbeat.service.DeviceStatusService;
import com.zishuo.fleet.module.heartbeat.ws.DeviceStatusBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 心跳消息消费者 - 从 Kafka 消费,更新 Redis + Postgres + 推送 WebSocket。
 *
 * <h2>消费流程</h2>
 * <ol>
 *   <li>从 device-heartbeats topic 拉消息</li>
 *   <li>调 DeviceStatusService 更新 Redis(在线状态)</li>
 *   <li>调 DeviceRepository.updateHeartbeat 更新 Postgres</li>
 *   <li>调 DeviceStatusBroadcaster 通过 WebSocket 推送</li>
 *   <li>成功后调 acknowledgment.acknowledge() 提交 offset</li>
 * </ol>
 *
 * <h2>错误处理</h2>
 * 处理失败时不 ack,Kafka 会重新投递这条消息。
 * 配合应用层幂等(updateHeartbeat 是 UPDATE 语句),实现 at-least-once 投递语义。
 *
 * <h2>并发</h2>
 * KafkaConfig 里设置了 concurrency=3,会启动 3 个消费线程并行消费。
 * 同一 partition 内的消息严格串行,跨 partition 可并行。
 *
 * <h2>@Transactional 的位置</h2>
 * 注意:@Transactional 必须放在 public 方法上(且不能由同类内部调用,
 * 因为 Spring AOP 代理基于子类,内部调用不走代理)。
 * 这里直接放在 consume 方法上,整个处理流程在一个事务内。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HeartbeatConsumer {

    private final DeviceStatusService statusService;
    private final DeviceRepository deviceRepository;
    private final DeviceStatusBroadcaster broadcaster;

    /**
     * 监听 device-heartbeats topic。
     */
    @KafkaListener(
            topics = KafkaTopics.HEARTBEAT,
            containerFactory = "heartbeatListenerContainerFactory"
    )
    @Transactional
    public void consume(HeartbeatEvent event, Acknowledgment ack) {
        log.debug("Received heartbeat: tenant={} device={} battery={}",
                event.tenantId(), event.deviceId(), event.batteryLevel());

        try {
            // 1. 更新 Redis 在线状态
            statusService.markOnline(event);

            // 2. 更新 Postgres(高效 UPDATE,不加载实体)
            int updated = deviceRepository.updateHeartbeat(
                    event.deviceId(),
                    event.tenantId(),
                    event.timestamp(),
                    event.batteryLevel(),
                    event.location());

            if (updated == 0) {
                log.warn("Device not found for heartbeat: tenant={} device={}",
                        event.tenantId(), event.deviceId());
                ack.acknowledge();   // 设备不存在不算错误,确认消息避免无限重试
                return;
            }

            // 3. 通过 WebSocket 推送状态变更
            DeviceStatusUpdate update = new DeviceStatusUpdate(
                    event.deviceId(),
                    event.serialNumber(),
                    DeviceStatus.ONLINE,
                    event.timestamp(),
                    event.batteryLevel(),
                    event.location());
            broadcaster.broadcast(event.tenantId(), update);

            // 处理成功,提交 offset
            ack.acknowledge();
        } catch (Exception e) {
            // 不 ack,Kafka 会重新投递。业务逻辑必须幂等。
            log.error("Failed to process heartbeat tenant={} device={}: {}",
                    event.tenantId(), event.deviceId(), e.getMessage(), e);
            throw e;
        }
    }
}

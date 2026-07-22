package com.zishuo.fleet.module.heartbeat.producer;

import com.zishuo.fleet.infrastructure.kafka.KafkaTopics;
import com.zishuo.fleet.module.heartbeat.domain.HeartbeatEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 心跳消息生产者 - 把 HeartbeatEvent 发到 Kafka。
 *
 * <h2>为什么用独立的 Producer 类?</h2>
 * Controller 不直接调 KafkaTemplate,而是通过这个 Producer:
 * <ul>
 *   <li>封装 topic 名、key 提取逻辑</li>
 *   <li>统一日志、错误处理</li>
 *   <li>便于单元测试时 mock</li>
 * </ul>
 *
 * <h2>partition key 的选择</h2>
 * 用 tenantId 当 key:
 * <ul>
 *   <li>同一租户的所有设备心跳进同一分区</li>
 *   <li>分区内有序,保证同租户事件按时序处理</li>
 *   <li>不同租户可以并行处理,扩展性好</li>
 * </ul>
 *
 * <h2>异步 send 还是同步 send?</h2>
 * KafkaTemplate.send() 返回 CompletableFuture,默认是异步的。
 * 我们用异步:Controller 调用立即返回,不等 Kafka ack,响应快。
 * 用 whenComplete 处理成功/失败日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HeartbeatProducer {

    private final KafkaTemplate<String, HeartbeatEvent> kafkaTemplate;

    /**
     * 发送心跳事件到 Kafka。
     *
     * @param event 心跳事件,tenantId 会用作 partition key
     */
    public void send(HeartbeatEvent event) {
        // partition key = tenantId 的字符串形式
        String key = String.valueOf(event.tenantId());

        CompletableFuture<SendResult<String, HeartbeatEvent>> future =
                kafkaTemplate.send(KafkaTopics.HEARTBEAT, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                // 发送失败 - 这种情况比较少见,只在 Kafka 宕机或网络隔离时发生
                log.error("Failed to send heartbeat for device {} (tenant {}): {}",
                        event.deviceId(), event.tenantId(), ex.getMessage());
            } else {
                log.debug("Heartbeat sent: device={} tenant={} partition={} offset={}",
                        event.deviceId(), event.tenantId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}

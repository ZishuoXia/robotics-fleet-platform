package com.zishuo.fleet.module.heartbeat.domain;

import java.time.Instant;
import java.util.Map;

/**
 * 设备心跳事件 - 在 Kafka topic 里流转的消息体。
 *
 * <h2>用 record 的原因</h2>
 * <ul>
 *   <li>不可变 - 消息一旦发出就不应被修改</li>
 *   <li>简洁 - 自动生成构造器、getter、equals、hashCode</li>
 *   <li>JSON 序列化友好 - Jackson 完美支持 record</li>
 * </ul>
 *
 * <h2>字段说明</h2>
 * <ul>
 *   <li>{@code tenantId}:租户 ID,作为 Kafka 消息的 partition key,
 *       保证同租户消息进同一分区有序</li>
 *   <li>{@code deviceId}:设备 ID</li>
 *   <li>{@code serialNumber}:序列号,冗余字段,便于排查不需要 JOIN</li>
 *   <li>{@code timestamp}:设备本地的心跳时间(理论上,实际心跳延迟<1s)</li>
 *   <li>{@code batteryLevel}:电量百分比 0-100,nullable</li>
 *   <li>{@code location}:位置 "lat,lon" 字符串,nullable</li>
 *   <li>{@code metrics}:扩展指标(温度、CPU、内存等),用 Map 灵活承载</li>
 * </ul>
 */
public record HeartbeatEvent(
        Long tenantId,
        Long deviceId,
        String serialNumber,
        Instant timestamp,
        Integer batteryLevel,
        String location,
        Map<String, Object> metrics
) {
}

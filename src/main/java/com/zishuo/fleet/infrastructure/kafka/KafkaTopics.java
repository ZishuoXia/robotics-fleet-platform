package com.zishuo.fleet.infrastructure.kafka;

/**
 * Kafka Topic 名常量。
 *
 * <h2>为什么集中管理 topic 名?</h2>
 * Topic 名是 Producer 和 Consumer 之间的"约定"——发到 topic-A 的消息只有
 * 订阅 topic-A 的 Consumer 能收到。
 *
 * 如果 Producer 写 "device-heartbeats",Consumer 写 "deviceHeartbeats",
 * 就会出现"消息发了但没人收"的诡异 bug。
 *
 * 集中常量化,避免字符串拼写错误。
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    /** 设备心跳消息 topic */
    public static final String HEARTBEAT = "device-heartbeats";
}

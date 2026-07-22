package com.zishuo.fleet.infrastructure.kafka;

import com.zishuo.fleet.module.heartbeat.domain.HeartbeatEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

/**
 * Kafka 配置 - 主要工作有两个:
 * <ol>
 *   <li>启动时自动创建 topic(可省去手动 kafka-topics.sh 创建)</li>
 *   <li>配置 Consumer 监听容器(并发数、ack 模式)</li>
 * </ol>
 *
 * <h2>为什么需要自定义 ContainerFactory?</h2>
 * Spring Boot 默认会创建一个 ContainerFactory,但默认配置:
 * <ul>
 *   <li>并发度 = 1(只用一个线程消费)</li>
 *   <li>ack 模式 = BATCH(自动批量提交)</li>
 * </ul>
 *
 * 我们要:
 * <ul>
 *   <li>并发度 = 3(对应 partition 数,3 个线程并行消费)</li>
 *   <li>ack 模式 = MANUAL_IMMEDIATE(每条消息处理完手动 ack,可靠性高)</li>
 * </ul>
 *
 * <h2>@EnableKafka</h2>
 * 激活 {@code @KafkaListener} 注解(Spring Boot 自动配置已包含,但显式加更清晰)。
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    /**
     * 启动时自动创建心跳 topic。
     *
     * <p>partitions = 3:消息会被分到 3 个分区,3 个消费者线程并行消费。
     * <p>同一 key(tenantId)的消息进同一分区,保证同租户消息顺序。
     *
     * <p>replicas = 1:开发环境单节点 Kafka,只能 1 副本。生产应该 ≥ 3。
     */
    @Bean
    public NewTopic heartbeatTopic() {
        return TopicBuilder.name(KafkaTopics.HEARTBEAT)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * 自定义 Kafka 监听容器工厂。
     * Spring 用它创建 @KafkaListener 注解标记的 Consumer。
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, HeartbeatEvent> heartbeatListenerContainerFactory(
            ConsumerFactory<String, HeartbeatEvent> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, HeartbeatEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // 并发度 3,对应 topic 的 3 个分区,3 个消费线程并行处理
        factory.setConcurrency(3);

        // 手动 ack 模式:消息处理完调 acknowledgment.acknowledge() 才标记已消费
        // 出错时不 ack,下次启动会重新消费(at-least-once 投递语义)
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        return factory;
    }
}

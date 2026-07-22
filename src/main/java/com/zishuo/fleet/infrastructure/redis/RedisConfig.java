package com.zishuo.fleet.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 模板配置。
 *
 * <h2>为什么需要自定义 RedisTemplate?</h2>
 * Spring Boot 自动配置的 RedisTemplate 默认用 JDK 序列化(把 Java 对象转字节流)。
 * 问题:
 * <ul>
 *   <li>用 redis-cli 查看时全是乱码二进制</li>
 *   <li>非 Java 客户端(Python、Go)无法读取</li>
 *   <li>JDK 序列化历史上有反序列化漏洞</li>
 * </ul>
 *
 * <h2>我们的配置</h2>
 * <ul>
 *   <li>key 用 String 序列化:redis-cli 友好</li>
 *   <li>value 用 JSON 序列化:跨语言、可读</li>
 *   <li>hash key/value 也分别用 String/JSON</li>
 * </ul>
 *
 * <h2>StringRedisTemplate vs RedisTemplate</h2>
 * <ul>
 *   <li>StringRedisTemplate:专门处理 String 数据,简单场景用它</li>
 *   <li>RedisTemplate<String, Object>:能存任何对象,我们用这个更通用</li>
 * </ul>
 */
@Configuration
public class RedisConfig {

    /**
     * 自定义 ObjectMapper - JSON 序列化器内部用。
     * 注册 JavaTimeModule 让 Instant、LocalDateTime 能正确序列化。
     */
    private ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        // 启用类型信息(让反序列化时能恢复原始类型,比如 HashMap 里的复杂对象)
        // 用 BasicPolymorphicTypeValidator 防止反序列化漏洞,只允许特定包的类
        BasicPolymorphicTypeValidator validator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.zishuo.fleet")
                .allowIfSubType("java.util")
                .allowIfSubType("java.lang")
                .allowIfSubType("java.time")
                .build();
        mapper.activateDefaultTyping(validator,
                ObjectMapper.DefaultTyping.NON_FINAL);
        return mapper;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // key 用 String 序列化:redis-cli 看到的就是普通字符串
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // value 用 JSON 序列化:redis-cli 看到的是 JSON 字符串
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}

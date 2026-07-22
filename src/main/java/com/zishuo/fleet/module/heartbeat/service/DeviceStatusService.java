package com.zishuo.fleet.module.heartbeat.service;

import com.zishuo.fleet.infrastructure.redis.RedisKeys;
import com.zishuo.fleet.module.device.domain.DeviceStatus;
import com.zishuo.fleet.module.heartbeat.domain.HeartbeatEvent;
import com.zishuo.fleet.module.heartbeat.dto.DeviceStatusSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 设备实时状态服务 - 操作 Redis 的封装。
 *
 * <h2>Redis 数据模型</h2>
 * <pre>
 * Key: device:tenant:{tenantId}:{deviceId}:status   (String)
 *   Value: "ONLINE"
 *   TTL: 90 秒(配置项 app.heartbeat.online-ttl-seconds)
 *
 * Key: device:tenant:{tenantId}:{deviceId}:meta     (Hash)
 *   Fields:
 *     batteryLevel: "85"
 *     location:     "37.7749,-122.4194"
 *     lastSeenAt:   "2026-05-25T08:00:00Z"
 *   TTL: 90 秒
 *
 * Key: tenant:{tenantId}:online-devices             (Set)
 *   Members: [42, 43, 44, ...]  设备 ID 集合
 *   (无 TTL,定期由心跳消费者维护)
 * </pre>
 *
 * <h2>为什么 status 和 meta 用两个 key 而不是一个?</h2>
 * <ul>
 *   <li>status 是热数据,查询频率最高,单独 String 类型最快</li>
 *   <li>meta 包含多字段,用 Hash 一次性读取所有字段更高效</li>
 *   <li>分开后未来可以独立调整 TTL 策略</li>
 * </ul>
 *
 * <h2>online-devices Set 的作用</h2>
 * 查询"某租户所有在线设备"时,如果只有上面两个 key,需要 SCAN 扫描所有 key,
 * 性能很差(O(n))。维护一个 Set 后,SMEMBERS 一次性返回所有 ID,O(1)。
 * 代价:每次心跳要额外 SADD,可接受。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceStatusService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.heartbeat.online-ttl-seconds:90}") //防御性编程，兜底，避免忘记在yml中设置此项从而报错
    private long onlineTtlSeconds;

    /**
     * 处理心跳:把设备标记为在线,更新元数据。
     */
    public void markOnline(HeartbeatEvent event) {
        Duration ttl = Duration.ofSeconds(onlineTtlSeconds);

        // 1. 设置 status = ONLINE,带 TTL
        String statusKey = RedisKeys.deviceStatus(event.tenantId(), event.deviceId());
        redisTemplate.opsForValue().set(statusKey, DeviceStatus.ONLINE.name(), ttl); //.name()把枚举转字符串
        // 等价于redis命令：SET device:tenant:1:42:status "ONLINE" PX 90000
        //                                                        ↑
        //                                             毫秒级 TTL(Spring 内部转换)

        // 2. 设置元数据 hash
        String metaKey = RedisKeys.deviceMeta(event.tenantId(), event.deviceId());
        Map<String, Object> meta = new HashMap<>();
        meta.put("lastSeenAt", event.timestamp().toString());
        if (event.batteryLevel() != null) {
            meta.put("batteryLevel", event.batteryLevel().toString());
        }
        if (event.location() != null) {
            meta.put("location", event.location());
        }
        redisTemplate.opsForHash().putAll(metaKey, meta); // 等价于redis命令:HSET device:tenant:1:42:meta lastSeenAt "2026-05-25T08:00:00Z" batteryLevel "85" location "..."
        redisTemplate.expire(metaKey, ttl); // 等价于redis命令:EXPIRE device:tenant:1:42:meta 90

        // 3. 加入租户在线设备集合
        String onlineSetKey = RedisKeys.tenantOnlineDevices(event.tenantId());
        redisTemplate.opsForSet().add(onlineSetKey, event.deviceId().toString());
        // 等价于redis: SADD tenant:1:online-devices "42"
        // 注意:这里没有给 Set 设 TTL——Set 永久存在,由 markOffline 主动维护

        log.debug("Marked online: tenant={} device={}", event.tenantId(), event.deviceId());
    }

    /**
     * 标记设备离线:删除 Redis 状态,从在线集合移除。
     * 由 OfflineDetector 定时任务调用。
     * 注意参数不是 HeartbeatEvent,而是 Long tenantId, Long deviceId。
     * 为什么?
     * markOnline 由心跳触发,有完整的 HeartbeatEvent 对象。
     * markOffline 由定时任务触发(OfflineDetector),那时只有 deviceId(从 Postgres 查出的),没有 HeartbeatEvent。
     */
    public void markOffline(Long tenantId, Long deviceId) {
        redisTemplate.delete(RedisKeys.deviceStatus(tenantId, deviceId));
        redisTemplate.delete(RedisKeys.deviceMeta(tenantId, deviceId));
        redisTemplate.opsForSet().remove(
                RedisKeys.tenantOnlineDevices(tenantId), deviceId.toString());

        log.debug("Marked offline: tenant={} device={}", tenantId, deviceId);
    }

    /**
     * 查询单个设备的实时状态。
     * 不返回 Device 的完整信息(name 等),只返回 Redis 里的实时数据。
     * Controller 会和 Postgres 数据合并后返回 DeviceStatusSnapshot 给前端。
     */
    public DeviceStatusSnapshot getRealtimeStatus(Long tenantId, Long deviceId) {
        String statusKey = RedisKeys.deviceStatus(tenantId, deviceId);
        Object statusVal = redisTemplate.opsForValue().get(statusKey);

        if (statusVal == null) {
            // Redis 里没有 -> 设备离线
            return new DeviceStatusSnapshot(
                    deviceId, null, null, DeviceStatus.OFFLINE,
                    null, null, null);
        }

        DeviceStatus status = DeviceStatus.valueOf(statusVal.toString());
        String metaKey = RedisKeys.deviceMeta(tenantId, deviceId);
        Map<Object, Object> meta = redisTemplate.opsForHash().entries(metaKey);

        Integer battery = meta.get("batteryLevel") != null
                ? Integer.parseInt(meta.get("batteryLevel").toString()) : null;
        String location = meta.get("location") != null
                ? meta.get("location").toString() : null;
        Instant lastSeen = meta.get("lastSeenAt") != null
                ? Instant.parse(meta.get("lastSeenAt").toString()) : null;

        return new DeviceStatusSnapshot(
                deviceId, null, null, status, lastSeen, battery, location);
    }

    /**
     * 查询某租户所有在线设备 ID。
     * 用于 GET /api/v1/devices/online 接口。
     */
    public Set<Long> getOnlineDeviceIds(Long tenantId) {
        String key = RedisKeys.tenantOnlineDevices(tenantId);
        Set<Object> members = redisTemplate.opsForSet().members(key);
        if (members == null || members.isEmpty()) {
            return new HashSet<>();
        }
        return members.stream()
                .map(m -> Long.parseLong(m.toString()))
                .collect(Collectors.toSet());
    }
}







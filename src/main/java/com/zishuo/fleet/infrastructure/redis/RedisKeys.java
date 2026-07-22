package com.zishuo.fleet.infrastructure.redis;

/**
 * Redis Key 命名规范常量。
 *
 * <h2>为什么集中管理 key?</h2>
 * Redis 是个巨大的 key-value 池子,所有 key 都在同一个 namespace。
 * 如果各个 Service 自己拼字符串,容易出现:
 * <ul>
 *   <li>命名风格不一致(下划线 vs 冒号)</li>
 *   <li>key 冲突</li>
 *   <li>修改 key 格式时改不全</li>
 * </ul>
 * 把所有 key 模板集中在一个类,确保规范统一。
 *
 * <h2>本项目命名规范</h2>
 * <pre>
 * device:tenant:{tenantId}:{deviceId}:status   设备在线状态(String, TTL 90s)
 * device:tenant:{tenantId}:{deviceId}:meta     设备元数据(Hash, TTL 90s)
 * tenant:{tenantId}:online-devices             某租户在线设备集合(Set)
 * </pre>
 *
 * 用冒号分层(Redis 工具友好,RedisInsight 等会自动折叠成树形)。
 * 带 tenant 前缀,实现多租户数据隔离。
 */
public final class RedisKeys {

    private RedisKeys() {} // 工具类,不允许实例化

    /** 设备在线状态 key:device:tenant:{tenantId}:{deviceId}:status */
    public static String deviceStatus(Long tenantId, Long deviceId) {
        return "device:tenant:" + tenantId + ":" + deviceId + ":status";
    }

    /** 设备元数据(电量、位置等)key:device:tenant:{tenantId}:{deviceId}:meta */
    public static String deviceMeta(Long tenantId, Long deviceId) {
        return "device:tenant:" + tenantId + ":" + deviceId + ":meta";
    }

    /** 某租户在线设备集合 key:tenant:{tenantId}:online-devices */
    public static String tenantOnlineDevices(Long tenantId) {
        return "tenant:" + tenantId + ":online-devices";
    }

    /** 扫描某租户所有设备状态 key 的模式 */
    public static String tenantDeviceStatusPattern(Long tenantId) {
        return "device:tenant:" + tenantId + ":*:status";
    }
}

package com.zishuo.fleet.module.device.domain;

/**
 * 设备生命周期状态枚举。
 *
 * <h2>状态机</h2>
 * <pre>
 *   PROVISIONED  ──首次连接──▶ ONLINE
 *        │                       │
 *        │                       │ 心跳超时
 *        │                       ▼
 *        │                    OFFLINE
 *        │                       │
 *        │                       │ 重新连接
 *        │                       │
 *        ▼                       ▼
 *   DECOMMISSIONED ◀──手动注销── 任意状态
 * </pre>
 *
 * <h2>为什么用 enum 而不是 String/int</h2>
 * <ul>
 *   <li>类型安全 - 不能传错值</li>
 *   <li>switch 时编译器会检查穷举性</li>
 *   <li>JPA 用 @Enumerated(STRING) 把名字存到数据库,可读性比数字编码好</li>
 * </ul>
 */
public enum DeviceStatus {
    /** 已注册但从未连接过的设备 */
    PROVISIONED,
    /** 在线 - 最近有心跳上报 */
    ONLINE,
    /** 离线 - 心跳超时或主动断开 */
    OFFLINE,
    /** 已退役 - 不再使用,数据保留用于审计 */
    DECOMMISSIONED
}

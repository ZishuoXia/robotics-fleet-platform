package com.zishuo.fleet.module.device.domain;

import com.zishuo.fleet.module.auth.domain.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 设备实体 - 系统管理的核心业务对象(机器人单元)。
 *
 * <h2>关键设计:tenantId 用普通列而不是 @ManyToOne</h2>
 * 这里有意没有用 {@code @ManyToOne private Tenant tenant;}:
 * <ul>
 *   <li>查询"这个租户有哪些设备"只需 tenant_id 列即可,不需要 join</li>
 *   <li>Device 模块和 Auth 模块解耦 - 不互相导入实体类</li>
 *   <li>未来如果拆成微服务,Device 服务不需要知道 Tenant 的内部结构</li>
 * </ul>
 *
 * <h2>租户隔离机制</h2>
 * 所有查询都带 tenantId 条件(见 DeviceRepository),
 * Service 层再做一次 tenantId 校验(见 DeviceService.findOwnedDevice),
 * 双层保险防止跨租户访问。
 *
 * <h2>M2 阶段扩展</h2>
 * 新增 batteryLevel、lastKnownLocation 用于实时监控。
 * lastSeenAt 在 M1 已有,M2 阶段会被心跳消费者频繁更新(每次心跳)。
 */
@Entity
@Table(name = "devices")
@Getter
@Setter
@NoArgsConstructor
public class Device extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属租户的 ID(普通列,不是 JPA 关联) */
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** 设备序列号,全局唯一 */
    @Column(name = "serial_number", nullable = false, unique = true, length = 128)
    private String serialNumber;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 64)
    private String model;

    @Column(name = "firmware_version", length = 32)
    private String firmwareVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeviceStatus status = DeviceStatus.PROVISIONED;

    /**
     * 最后一次心跳时间。
     * <p>M1:仅用于显示。
     * <p>M2:心跳消费者每次收到心跳都会更新它,定时任务依据此字段检测离线。
     */
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    /** 设备电量百分比(0-100),M2 新增 */
    @Column(name = "battery_level")
    private Integer batteryLevel;

    /** 最后已知位置,格式 "lat,lon" 或 GeoJSON 字符串,M2 新增 */
    @Column(name = "last_known_location", length = 255)
    private String lastKnownLocation;
}

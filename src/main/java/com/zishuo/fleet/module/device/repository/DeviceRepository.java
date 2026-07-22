package com.zishuo.fleet.module.device.repository;

import com.zishuo.fleet.module.device.domain.Device;
import com.zishuo.fleet.module.device.domain.DeviceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 设备数据访问层。
 *
 * <h2>查询方法都带 tenantId</h2>
 * 多租户隔离的第一道防线。任何想"按 id 查设备"的方法都要求传入 tenantId。
 *
 * <h2>M2 新增方法</h2>
 * <ul>
 *   <li>{@link #findStaleOnlineDevices} 查找心跳超时的在线设备(定时任务用)</li>
 *   <li>{@link #updateHeartbeat} 高效更新心跳字段(只 UPDATE 几列,不加载整个 Entity)</li>
 *   <li>{@link #markOffline} 批量标记离线</li>
 * </ul>
 */
public interface DeviceRepository extends JpaRepository<Device, Long> {

    /* ============= M1 方法 ============= */

    Optional<Device> findByIdAndTenantId(Long id, Long tenantId);

    Optional<Device> findBySerialNumber(String serialNumber);

    boolean existsBySerialNumber(String serialNumber);

    Page<Device> findByTenantId(Long tenantId, Pageable pageable);

    Page<Device> findByTenantIdAndStatus(Long tenantId, DeviceStatus status, Pageable pageable);

    /* ============= M2 新增 ============= */

    /**
     * 查找心跳超时的在线设备。
     * 定时任务用,扫描所有租户的设备(不带 tenantId,因为这是系统级任务)。
     *
     * @param threshold 阈值时间,last_seen_at 早于这个时间的视为超时
     * @return 超时设备列表
     */
    @Query("SELECT d FROM Device d WHERE d.status = 'ONLINE' AND d.lastSeenAt < :threshold")
    List<Device> findStaleOnlineDevices(@Param("threshold") Instant threshold);

    /**
     * 高效更新设备心跳字段(不需要先查后改,直接 UPDATE)。
     * 比 findById + setter + save 性能好得多——只有一次 SQL,且不加载整个对象。
     *
     * <p>{@code @Modifying} 告诉 Spring 这是个修改查询(默认是 SELECT)。
     *
     * @return 受影响行数(应该是 1,如果是 0 说明 device 不存在)
     */
    @Modifying
    @Query("UPDATE Device d SET " +
            "d.lastSeenAt = :lastSeenAt, " +
            "d.batteryLevel = :batteryLevel, " +
            "d.lastKnownLocation = :location, " +
            "d.status = 'ONLINE' " +   // 心跳到达,自动转 ONLINE
            "WHERE d.id = :id AND d.tenantId = :tenantId")
    int updateHeartbeat(@Param("id") Long id,
                        @Param("tenantId") Long tenantId,
                        @Param("lastSeenAt") Instant lastSeenAt,
                        @Param("batteryLevel") Integer batteryLevel,
                        @Param("location") String location);

    /**
     * 批量标记设备为离线。
     * 定时任务用,把多个超时设备一次性标记。
     *
     * @return 受影响行数
     */
    @Modifying
    @Query("UPDATE Device d SET d.status = 'OFFLINE' WHERE d.id IN :ids")
    int markOffline(@Param("ids") List<Long> ids);
}

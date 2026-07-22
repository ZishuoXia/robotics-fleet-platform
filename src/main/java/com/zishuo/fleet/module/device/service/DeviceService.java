package com.zishuo.fleet.module.device.service;

import com.zishuo.fleet.common.exception.BusinessException;
import com.zishuo.fleet.common.response.ErrorCode;
import com.zishuo.fleet.module.device.domain.Device;
import com.zishuo.fleet.module.device.domain.DeviceStatus;
import com.zishuo.fleet.module.device.dto.CreateDeviceRequest;
import com.zishuo.fleet.module.device.dto.DeviceResponse;
import com.zishuo.fleet.module.device.dto.UpdateDeviceRequest;
import com.zishuo.fleet.module.device.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 设备业务服务 - 核心 CRUD + 租户隔离逻辑。
 *
 * <h2>租户隔离的双层防御</h2>
 * <ol>
 *   <li>Repository 查询都强制带 tenantId 条件</li>
 *   <li>本类的 {@link #findOwnedDevice} 再做一次 tenantId 校验</li>
 * </ol>
 * 即使开发者错用了不带 tenant 的 findById,也能在第二层被拦下。
 *
 * <h2>方法签名约定</h2>
 * 所有"操作单个设备"的方法第一个参数都是 tenantId,这是显式的设计 -
 * 强迫调用方先想清楚"哪个租户在操作"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;

    /** 创建新设备(注册到当前租户下) */
    @Transactional
    public DeviceResponse create(Long tenantId, CreateDeviceRequest req) {
        // 序列号全局去重
        if (deviceRepository.existsBySerialNumber(req.serialNumber())) {
            throw new BusinessException(ErrorCode.DEVICE_SERIAL_TAKEN);
        }

        Device device = new Device();
        device.setTenantId(tenantId);
        device.setSerialNumber(req.serialNumber());
        device.setName(req.name());
        device.setModel(req.model());
        device.setFirmwareVersion(req.firmwareVersion());
        device.setStatus(DeviceStatus.PROVISIONED); // 初始状态:已注册未上线

        Device saved = deviceRepository.save(device);
        log.info("Device {} (sn={}) created in tenant {}",
                saved.getId(), saved.getSerialNumber(), tenantId);
        return DeviceResponse.from(saved);
    }

    /** 按 ID 查询设备详情(带租户校验) */
    @Transactional(readOnly = true)
    public DeviceResponse getById(Long tenantId, Long deviceId) {
        Device device = findOwnedDevice(tenantId, deviceId);
        return DeviceResponse.from(device);
    }

    /** 分页查询当前租户的设备列表,可按状态筛选 */
    @Transactional(readOnly = true)
    public Page<DeviceResponse> list(Long tenantId, DeviceStatus status, Pageable pageable) {
        Page<Device> page = (status == null)
                ? deviceRepository.findByTenantId(tenantId, pageable)
                : deviceRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        // Page.map 把 Page<Device> 转换成 Page<DeviceResponse>,保留分页元数据
        return page.map(DeviceResponse::from); //优雅的批量转换。 使用 Page.map()对每一页的 Device实体应用 DeviceResponse.from转换，最终得到 Page<DeviceResponse>，完美保留了分页信息。
    }

    /** 部分更新设备字段(只更新非 null 的字段) */
    @Transactional
    public DeviceResponse update(Long tenantId, Long deviceId, UpdateDeviceRequest req) {
        Device device = findOwnedDevice(tenantId, deviceId);
        if (req.name() != null)            device.setName(req.name());
        if (req.model() != null)           device.setModel(req.model());
        if (req.firmwareVersion() != null) device.setFirmwareVersion(req.firmwareVersion());
        return DeviceResponse.from(deviceRepository.save(device));
    }

    /** 退役设备 - 软删除,只是改 status,保留记录 */
    @Transactional
    public void decommission(Long tenantId, Long deviceId) {
        Device device = findOwnedDevice(tenantId, deviceId);
        device.setStatus(DeviceStatus.DECOMMISSIONED);
        deviceRepository.save(device);
        log.info("Device {} decommissioned by tenant {}", deviceId, tenantId);
    }

    /**
     * 私有工具:查询设备并验证它属于调用方租户。
     *
     * <p><b>关键安全设计</b>:跨租户访问伪装为 NOT_FOUND 而不是 FORBIDDEN。
     * 这样攻击者无法通过 403 vs 404 推断"哪些 ID 是存在的设备"。
     */
    private Device findOwnedDevice(Long tenantId, Long deviceId) { // 注意：如果直接使用 findByIdAndTenantId， 无法区分“不存在”和“越权”
        Device device = deviceRepository.findById(deviceId) // 第一层查询（潜在漏洞点）。 这里使用了不带租户 ID 的查询（findById）。这正是类注释中提到的“即使开发者错用了不带 tenant 的 findById”的场景。
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));
        if (!device.getTenantId().equals(tenantId)) { // 第二层防御（核心）。 即使上一步成功返回了设备（说明 ID 在数据库中存在），也要检查该设备的 tenantId是否与调用方传入的一致。
            // 记录警告日志(便于排查可疑访问),但响应给客户端的是 NOT_FOUND
            log.warn("Tenant {} attempted to access device {} which belongs to tenant {}",
                    tenantId, deviceId, device.getTenantId());
            throw new BusinessException(ErrorCode.DEVICE_NOT_FOUND);
        }
        return device;
    }
}

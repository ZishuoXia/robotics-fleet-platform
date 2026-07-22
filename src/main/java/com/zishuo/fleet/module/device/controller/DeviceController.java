package com.zishuo.fleet.module.device.controller;

import com.zishuo.fleet.common.response.ApiResponse;
import com.zishuo.fleet.common.response.PageResponse;
import com.zishuo.fleet.module.device.domain.DeviceStatus;
import com.zishuo.fleet.module.device.dto.CreateDeviceRequest;
import com.zishuo.fleet.module.device.dto.DeviceResponse;
import com.zishuo.fleet.module.device.dto.UpdateDeviceRequest;
import com.zishuo.fleet.module.device.service.DeviceService;
import com.zishuo.fleet.security.service.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 设备模块 Controller - 提供设备的 CRUD REST 接口。
 *
 * <h2>权限模型</h2>
 * 所有方法都用 {@code @PreAuthorize} 检查权限:
 * <ul>
 *   <li>读类操作要求 {@code device:read}</li>
 *   <li>写类操作要求 {@code device:write}</li>
 * </ul>
 * VIEWER 角色只有 device:read,OPERATOR 和 ADMIN 都有 device:write。
 *
 * <h2>RESTful 设计</h2>
 * <table>
 *   <tr><th>HTTP 方法</th><th>路径</th><th>语义</th></tr>
 *   <tr><td>POST</td>   <td>/devices</td>     <td>创建</td></tr>
 *   <tr><td>GET</td>    <td>/devices/{id}</td><td>查单个</td></tr>
 *   <tr><td>GET</td>    <td>/devices</td>     <td>查列表(带分页)</td></tr>
 *   <tr><td>PATCH</td>  <td>/devices/{id}</td><td>部分更新</td></tr>
 *   <tr><td>DELETE</td> <td>/devices/{id}</td><td>退役</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Tag(name = "Devices", description = "Tenant-scoped device registry")
public class DeviceController {

    private final DeviceService deviceService;
    private final CurrentUserService currentUserService;

    /** 创建新设备 - 自动归属到调用者所在租户 */
    @PostMapping
    @PreAuthorize("hasAuthority('device:write')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new device in the caller's tenant")
    public ApiResponse<DeviceResponse> create(@Valid @RequestBody CreateDeviceRequest req) {
        Long tenantId = currentUserService.getCurrentTenantId();
        return ApiResponse.ok(deviceService.create(tenantId, req));
    }

    /** 查询单个设备详情 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('device:read')")
    @Operation(summary = "Get a single device by id")
    public ApiResponse<DeviceResponse> getById(@PathVariable Long id) {
        Long tenantId = currentUserService.getCurrentTenantId();
        return ApiResponse.ok(deviceService.getById(tenantId, id));
    }

    /**
     * 分页查询设备列表。
     *
     * <p>{@code @PageableDefault(size = 20)}:如果客户端没传分页参数,默认每页 20 条。
     * 客户端可以传 {@code ?page=0&size=10&sort=createdAt,desc} 来覆盖。
     */
    @GetMapping
    @PreAuthorize("hasAuthority('device:read')")
    @Operation(summary = "List devices in the caller's tenant, optionally filtered by status")
    public ApiResponse<PageResponse<DeviceResponse>> list(
            @RequestParam(required = false) DeviceStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Long tenantId = currentUserService.getCurrentTenantId();
        Page<DeviceResponse> page = deviceService.list(tenantId, status, pageable);
        return ApiResponse.ok(PageResponse.from(page));
    }

    /** 部分更新 - PATCH 语义,只改传过来的字段 */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('device:write')")
    @Operation(summary = "Update mutable fields of a device")
    public ApiResponse<DeviceResponse> update(@PathVariable Long id,
                                              @Valid @RequestBody UpdateDeviceRequest req) {
        Long tenantId = currentUserService.getCurrentTenantId();
        return ApiResponse.ok(deviceService.update(tenantId, id, req));
    }

    /** 退役设备 - 软删除,只改状态 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('device:write')")
    @Operation(summary = "Decommission (soft-delete) a device")
    public ApiResponse<Void> decommission(@PathVariable Long id) {
        Long tenantId = currentUserService.getCurrentTenantId();
        deviceService.decommission(tenantId, id);
        return ApiResponse.ok();
    }
}

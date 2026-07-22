package com.zishuo.fleet.module.device.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新设备请求 DTO。
 *
 * <p>所有字段都是可选的(没有 @NotBlank),传哪个改哪个。
 * 配合 PATCH 方法实现"部分更新"的语义。
 *
 * <p>不允许通过更新接口修改 status / serialNumber - 这些有专门的接口。
 */
public record UpdateDeviceRequest(
        @Size(max = 128) String name,
        @Size(max = 64) String model,
        @Size(max = 32) String firmwareVersion
) {}

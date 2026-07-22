package com.zishuo.fleet.module.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建设备请求 DTO。
 *
 * <p>注意没有 tenantId 字段 - 租户 ID 是从 JWT 自动取的,不需要客户端传。
 * 这避免了"伪造 tenantId 把设备创建到别人租户下"的攻击。
 */
public record CreateDeviceRequest( // record本质上就是"确保这次请求的值不被修改"。避免同一个req在被多个服务使用的时候，有人对req不小心进行了修改，导致其他服务也跟着出错。
        @NotBlank
        @Size(max = 128)
        String serialNumber,

        @NotBlank
        @Size(max = 128)
        String name,

        @Size(max = 64)
        String model,

        @Size(max = 32)
        String firmwareVersion
) {}

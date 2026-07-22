package com.zishuo.fleet.module.auth.dto;

/**
 * 登录成功后返回给客户端的 token 信息。
 *
 * <h2>字段含义</h2>
 * <ul>
 *   <li>accessToken: 实际的 JWT 字符串</li>
 *   <li>tokenType: 固定为 "Bearer",符合 OAuth 2.0 标准</li>
 *   <li>expiresInSeconds: token 有效期(秒);客户端可以据此提前刷新</li>
 * </ul>
 *
 * <h2>客户端用法</h2>
 * 后续每个请求都要带 header: {@code Authorization: Bearer <accessToken>}
 */
public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {
    /** 工厂方法,自动填 "Bearer" 类型,简化调用方代码 */
    public static TokenResponse bearer(String token, long expiresInSeconds) {
        return new TokenResponse(token, "Bearer", expiresInSeconds);
    }
}

package com.zishuo.fleet.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zishuo.fleet.common.response.ApiResponse;
import com.zishuo.fleet.common.response.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 权限不足处理器 - 已认证用户访问没有权限的资源时调用。
 *
 * <h2>触发场景</h2>
 * 用户已登录(JWT 有效),但他的权限码不满足 {@code @PreAuthorize} 的要求。
 * 例如普通 VIEWER 用户调用 {@code @PreAuthorize("hasAuthority('device:write')")} 的接口。
 *
 * <h2>语义区分</h2>
 * <ul>
 *   <li>401 UNAUTHORIZED:你是谁我不知道(未认证)</li>
 *   <li>403 FORBIDDEN:我知道你是谁,但你没权限</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class JsonAccessDeniedHandler implements AccessDeniedHandler { //和JsonAuthenticationEntryPoint类几乎一样
    //这个类最终是返回403，无权限访问某接口。可以参考上面的绿色注释。
    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> body = ApiResponse.error(ErrorCode.FORBIDDEN);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}

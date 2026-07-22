package com.zishuo.fleet.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zishuo.fleet.common.response.ApiResponse;
import com.zishuo.fleet.common.response.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/**
 * 未认证请求的入口处理器 - 在 Spring Security 检测到"未认证访问受保护资源"时调用。
 *
 * <h2>为什么需要这个</h2>
 * Spring Security 默认会返回 HTML 错误页或简单的 401,
 * 这跟我们的 ApiResponse 格式不一致。本类强制写入统一格式的 JSON 响应。
 *
 * <h2>触发场景</h2>
 * <ul>
 *   <li>请求没带 Authorization header,但访问的是受保护接口</li>
 *   <li>Authorization header 里的 token 无效(签名错、过期等)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
// 一句话解释这个类：当"没登录的人"想访问"需要登录的接口"时,由这个类负责返回一个统一格式的 401 JSON 响应
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint { //实现了这个接口,就有资格被注册为"未认证处理器"。
    //为什么叫 "entry point"(入口点)?因为它代表"想进入系统但还没认证"的那个点——Spring Security 在这里"拦住你",让你先去认证
    /** 用 Spring Boot 已经配置好的 Jackson ObjectMapper(支持 Instant 等现代时间类型) */
    private final ObjectMapper objectMapper;  //这是 Jackson 库的核心类，用于 Java 对象 ↔ JSON 字符串的转换

    @Override
    public void commence(HttpServletRequest request,//封装"进来的请求"(URL、header、body、参数)，虽然我们这里没用，但是接口要求传
                         HttpServletResponse response,//封装"出去的响应"(状态码、header、body)
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); //设置状态码 401， Servlet 规范定义了一堆这种常量:  SC_OK = 200 SC_UNAUTHORIZED = 401 SC_FORBIDDEN = 403 SC_NOT_FOUND = 404 SC_INTERNAL_SERVER_ERROR = 500
        response.setContentType(MediaType.APPLICATION_JSON_VALUE); //设置响应类型，这一行告诉客户端:"我返回的是 JSON,不是 HTML 或纯文本"
        ApiResponse<Void> body = ApiResponse.error(
                ErrorCode.UNAUTHORIZED, "Authentication required");//复用 ApiResponse,让 401 响应和正常接口、和 GlobalExceptionHandler 的响应格式完全一致。前端只写一套解析逻辑
        objectMapper.writeValue(response.getOutputStream(), body); // 写 JSON
        //拿到响应的"输出流"(往客户端写数据的管道)   把 body 对象序列化成 JSON 并写进这个流
    }
}

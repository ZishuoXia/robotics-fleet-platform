package com.zishuo.fleet.common.response;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Lightweight projection of Spring Data's {@link Page}. We don't return Page
 * directly because its serialized shape includes {@code Sort} and {@code Pageable}
 * fields that leak implementation detail.
 */
@Getter
@Builder
public class PageResponse<T> {
    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;

    public static <T> PageResponse<T> from(Page<T> page) {
        //为什么不直接返回 Spring 的 Page<T>?
        //Spring Data JPA 查分页给你一个 Page<T> 对象。你可以直接返回它,但它序列化出来的 JSON很长，可见claude
        return PageResponse.<T>builder()
                .content(page.getContent())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}

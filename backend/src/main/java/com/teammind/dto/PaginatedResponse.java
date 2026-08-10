package com.teammind.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * 分页响应 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaginatedResponse<T> {

    private List<T> items;
    private long total;
    private int page;
    private int pageSize;
    private boolean hasMore;

    public static <T> PaginatedResponse<T> of(List<T> items, long total, int page, int pageSize) {
        return PaginatedResponse.<T>builder()
                .items(items)
                .total(total)
                .page(page)
                .pageSize(pageSize)
                .hasMore((long) page * pageSize < total)
                .build();
    }
}

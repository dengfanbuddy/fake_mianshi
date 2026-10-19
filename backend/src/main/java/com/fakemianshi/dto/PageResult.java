package com.fakemianshi.dto;

import lombok.Data;

import java.util.List;
import java.util.function.Function;

/**
 * 分页结果统一结构。
 *
 * <p>原先列表接口一律返回全量数组（{@code SELECT * ... ORDER BY created_at DESC}），
 * 数据量增长后既拖慢响应也拖慢前端渲染。分页接口统一返回本结构，
 * 前端只需要额外的 {@code total} 来渲染页码。
 */
@Data
public class PageResult<T> {

    /** 当前页数据 */
    private List<T> records;

    /** 总记录数 */
    private long total;

    /** 当前页码（从 1 开始） */
    private int page;

    /** 每页条数 */
    private int size;

    /** 总页数 */
    private int totalPages;

    public static <T> PageResult<T> of(List<T> records, long total, int page, int size) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(total);
        result.setPage(page);
        result.setSize(size);
        result.setTotalPages(size <= 0 ? 0 : (int) ((total + size - 1) / size));
        return result;
    }

    /** 页码/每页条数归一化：页码至少 1，条数限制在 1..maxSize，避免被传 0 或超大值打爆内存 */
    public static int normalizePage(int page) {
        return Math.max(1, page);
    }

    public static int normalizeSize(int size, int maxSize) {
        return Math.min(maxSize, Math.max(1, size));
    }

    /** 列表映射（分页信息保持不变） */
    public <R> PageResult<R> map(Function<T, R> mapper) {
        return of(records == null ? List.of() : records.stream().map(mapper).toList(),
                total, page, size);
    }
}

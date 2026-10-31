package com.example.api.utils;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Pagination {

    private final Integer total;

    private final Integer limit;

    private final Integer offset;

    private final Integer pageSize;

    private final Integer pageCount;

    private final Integer currentPage;

    private Pagination(
        Integer total, Integer limit, Integer offset, 
        Integer pageSize, Integer pageCount, Integer currentPage
    ) {
        this.total          = total;
        this.limit          = limit;
        this.offset         = offset;
        this.pageSize       = pageSize;
        this.pageCount      = pageCount;
        this.currentPage    = currentPage;
    }

    public Integer getTotal() {
        return this.total;
    }

    @JsonIgnore
    public Integer getLimit() {
        return this.limit;
    }

    @JsonIgnore
    public Integer getOffset() {
        return this.offset;
    }

    public Integer getPageSize() {
        return this.pageSize;
    }

    public Integer getPageCount() {
        return this.pageCount;
    }

    public Integer getCurrentPage() {
        return this.currentPage;
    }

    public static Pagination paginate(
        Integer total, Integer pageSize, Integer currentPage
    ) {
        if (currentPage == null || currentPage < 1) {
            currentPage = 1;
        }

        if (pageSize == null || pageSize < 1) {
            pageSize    = 10;
        }

        Integer pageCount   = (int) Math.ceil(
            (double) total / (double) pageSize
        );

        if (pageCount < 1) {
            pageCount   = 1;
        }

        if (currentPage > pageCount) {
            currentPage = pageCount;
        }

        Integer offset      = (currentPage - 1) * pageSize;
        
        return new Pagination(
            total, pageSize, offset, pageSize, pageCount, currentPage
        );
    }

}

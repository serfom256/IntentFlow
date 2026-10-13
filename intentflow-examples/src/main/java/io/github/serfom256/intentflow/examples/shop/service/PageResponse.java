package io.github.serfom256.intentflow.examples.shop.service;

import io.github.serfom256.model.page.Page;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.content(), page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}

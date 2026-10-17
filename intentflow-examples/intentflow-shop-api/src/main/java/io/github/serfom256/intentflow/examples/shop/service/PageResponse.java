package io.github.serfom256.intentflow.examples.shop.service;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalItems) {
        return new PageResponse<>(items, page, size, totalItems, (int) ((totalItems + size - 1) / size));
    }
}

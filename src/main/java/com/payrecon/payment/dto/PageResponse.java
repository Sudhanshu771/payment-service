package com.payrecon.payment.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

// One page of results + the info a UI needs to show "page 2 of 7".
// We return this instead of Spring's Page object, so the JSON shape is ours and stable.
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}

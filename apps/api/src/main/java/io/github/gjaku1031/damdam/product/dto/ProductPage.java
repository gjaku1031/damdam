package io.github.gjaku1031.damdam.product.dto;

import java.util.List;

/**
 * 상품 목록 응답 {@code {products, total, page, limit}}
 *
 * @param products 현재 쪽 상품
 * @param total 조건에 맞는 전체 상품 수
 * @param page 쪽 번호(1부터)
 * @param limit 쪽 크기
 */
public record ProductPage(List<ProductResponse> products, long total, int page, int limit) {}

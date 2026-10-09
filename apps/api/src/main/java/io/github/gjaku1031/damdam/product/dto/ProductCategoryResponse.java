package io.github.gjaku1031.damdam.product.dto;

import java.util.UUID;

/**
 * 상품 분류
 *
 * @param id ID
 * @param name 분류명
 * @param productCount 삭제되지 않은 상품 수(비공개 포함)
 */
public record ProductCategoryResponse(UUID id, String name, int productCount) {}

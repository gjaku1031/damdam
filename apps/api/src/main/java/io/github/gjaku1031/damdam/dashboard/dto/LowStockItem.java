package io.github.gjaku1031.damdam.dashboard.dto;

import java.util.UUID;

/**
 * 재고가 적은 판매 옵션
 *
 * @param productId 상품 ID
 * @param productName 상품명
 * @param variantId 옵션 ID
 * @param variantName 옵션명
 * @param stock 현재 재고
 */
public record LowStockItem(UUID productId, String productName, UUID variantId, String variantName, int stock) {}

package io.github.gjaku1031.damdam.inventory.dto;

import java.util.UUID;

/**
 * 현재 재고와 재고 이력 합계가 다른 옵션
 *
 * @param productId 상품 ID
 * @param productName 상품명
 * @param variantId 옵션 ID
 * @param variantName 옵션명
 * @param stock 현재 재고
 * @param ledgerTotal 재고 이력의 변화량 합계, 이력이 없으면 0
 */
public record StockLedgerMismatch(UUID productId, String productName, UUID variantId, String variantName, int stock,
        long ledgerTotal) {}

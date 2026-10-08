package io.github.gjaku1031.damdam.inventory.dto;

/**
 * 재고 조정 결과
 *
 * @param adjustment 저장한 조정 기록
 * @param stock 조정 후 재고
 */
public record AdjustmentResult(InventoryAdjustmentResponse adjustment, int stock) {}

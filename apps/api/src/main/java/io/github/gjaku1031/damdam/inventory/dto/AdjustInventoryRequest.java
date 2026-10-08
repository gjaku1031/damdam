package io.github.gjaku1031.damdam.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 재고 조정 요청
 *
 * @param delta 재고 변화량, 양수는 입고·음수는 출고, 필수
 * @param reason 조정 사유, 공백 불가
 */
public record AdjustInventoryRequest(@NotNull Integer delta, @NotBlank String reason) {}

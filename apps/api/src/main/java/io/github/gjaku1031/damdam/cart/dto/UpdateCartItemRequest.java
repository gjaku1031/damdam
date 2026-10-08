package io.github.gjaku1031.damdam.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 장바구니 항목 수량 변경 요청
 *
 * @param quantity 새 수량(1 이상), 필수
 */
public record UpdateCartItemRequest(@NotNull @Min(1) Integer quantity) {}

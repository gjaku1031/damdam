package io.github.gjaku1031.damdam.cart.dto;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 장바구니 담기 요청
 *
 * @param variantId 상품 옵션 ID, 필수
 * @param quantity 담을 수량(1 이상), 이미 담긴 옵션이면 기존 수량에 더함
 */
public record AddCartItemRequest(@NotNull UUID variantId, @NotNull @Min(1) Integer quantity) {}

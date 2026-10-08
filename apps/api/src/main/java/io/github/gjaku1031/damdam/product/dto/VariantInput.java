package io.github.gjaku1031.damdam.product.dto;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 상품 옵션 입력
 *
 * {@code id}가 있으면 기존 옵션 수정, 없으면 새 옵션
 * 기존 옵션의 재고는 바꿀 수 없음
 *
 * @param id 기존 옵션 ID, 새 옵션이면 {@code null}
 * @param name 옵션명, 필수(최대 100자)
 * @param priceAmount 가격(원, 0 이상), 필수
 * @param stock 새 옵션의 초기 재고(0 이상, 생략 시 0), 기존 옵션은 생략하거나 현재 재고와 같아야 함(다르면 409)
 * @param isDefault 기본 옵션 여부, 아무 옵션도 지정하지 않으면 첫 옵션이 기본
 * @param active 판매 여부, 생략 시 판매 중
 */
public record VariantInput(
        UUID id,
        @NotNull @Size(max = 100) String name,
        @NotNull @Min(0) Integer priceAmount,
        @Min(0) Integer stock,
        Boolean isDefault,
        Boolean active) {}

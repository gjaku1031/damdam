package io.github.gjaku1031.damdam.product.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.gjaku1031.damdam.product.domain.ProductVariantEntity;

/**
 * 상품 옵션 응답
 *
 * @param id ID
 * @param createdAt 생성 시각
 * @param updatedAt 수정 시각
 * @param name 옵션명
 * @param priceAmount 가격
 * @param priceCurrency 통화
 * @param stock 재고
 * @param active 판매 여부
 * @param isDefault 기본 옵션 여부
 */
public record ProductVariantResponse(UUID id, Instant createdAt, Instant updatedAt, String name, int priceAmount,
        String priceCurrency, int stock, boolean active, boolean isDefault) {
    /**
     * 옵션 엔티티로 생성
     *
     * @return 옵션 응답
     */
    public static ProductVariantResponse from(ProductVariantEntity variant) {
        return new ProductVariantResponse(variant.getId(), variant.getCreatedAt(), variant.getUpdatedAt(),
                variant.getName(), variant.getPriceAmount(), variant.getPriceCurrency(), variant.getStock(),
                variant.isActive(), variant.isDefault());
    }
}

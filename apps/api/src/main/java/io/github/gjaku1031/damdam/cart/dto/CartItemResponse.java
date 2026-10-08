package io.github.gjaku1031.damdam.cart.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.gjaku1031.damdam.cart.domain.CartItemEntity;
import io.github.gjaku1031.damdam.product.domain.ProductEntity;
import io.github.gjaku1031.damdam.product.domain.ProductVariantEntity;

/**
 * 장바구니 항목 응답
 *
 * @param id ID
 * @param createdAt 담은 시각
 * @param updatedAt 수정 시각
 * @param quantity 수량
 * @param priceSnapshot 담을 때의 옵션 가격
 * @param priceCapturedAt 가격을 기록한 시각
 * @param variant 담은 옵션, 옵션이 없어졌으면 {@code null}
 */
public record CartItemResponse(UUID id, Instant createdAt, Instant updatedAt, int quantity, int priceSnapshot,
        Instant priceCapturedAt, Variant variant) {
    /**
     * 담은 옵션
     *
     * @param id 옵션 ID
     * @param createdAt 생성 시각
     * @param updatedAt 수정 시각
     * @param name 옵션명
     * @param priceAmount 현재 가격
     * @param priceCurrency 통화
     * @param stock 재고
     * @param active 판매 여부
     * @param isDefault 기본 옵션 여부
     * @param product 옵션의 상품, 상품이 없어졌으면 {@code null}
     */
    public record Variant(UUID id, Instant createdAt, Instant updatedAt, String name, int priceAmount,
            String priceCurrency, int stock, boolean active, boolean isDefault, Product product) {}

    /**
     * 옵션의 상품
     *
     * @param id 상품 ID
     * @param createdAt 생성 시각
     * @param updatedAt 수정 시각
     * @param deletedAt 삭제 시각, 삭제 전이면 {@code null}
     * @param name 상품명
     * @param description 설명
     * @param category 분류
     * @param visible 공개 여부
     */
    public record Product(UUID id, Instant createdAt, Instant updatedAt, Instant deletedAt, String name,
            String description, String category, boolean visible) {}

    /**
     * 항목과 옵션·상품 엔티티로 생성
     *
     * @param variant 옵션, 없으면 {@code null}
     * @param product 상품, 없으면 {@code null}
     * @return 장바구니 항목 응답
     */
    public static CartItemResponse of(CartItemEntity item, ProductVariantEntity variant, ProductEntity product) {
        Product productView = product == null ? null
                : new Product(product.getId(), product.getCreatedAt(), product.getUpdatedAt(), product.getDeletedAt(),
                        product.getName(), product.getDescription(), product.getCategory(), product.isVisible());
        Variant variantView = variant == null ? null
                : new Variant(variant.getId(), variant.getCreatedAt(), variant.getUpdatedAt(), variant.getName(),
                        variant.getPriceAmount(), variant.getPriceCurrency(), variant.getStock(), variant.isActive(),
                        variant.isDefault(), productView);
        return new CartItemResponse(item.getId(), item.getCreatedAt(), item.getUpdatedAt(), item.getQuantity(),
                item.getPriceSnapshot(), item.getPriceCapturedAt(), variantView);
    }
}

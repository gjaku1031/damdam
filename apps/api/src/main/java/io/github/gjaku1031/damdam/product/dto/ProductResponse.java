package io.github.gjaku1031.damdam.product.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.github.gjaku1031.damdam.product.domain.ProductEntity;

/**
 * 상품 응답
 *
 * 공개 응답은 판매 중인 옵션만 담음
 *
 * @param id ID
 * @param createdAt 생성 시각
 * @param updatedAt 수정 시각
 * @param deletedAt 삭제 시각, 삭제 전이면 {@code null}
 * @param name 상품명
 * @param description 설명
 * @param category 분류
 * @param visible 공개 여부
 * @param images 사진(표시 순서)
 * @param variants 옵션
 */
public record ProductResponse(UUID id, Instant createdAt, Instant updatedAt, Instant deletedAt, String name,
        String description, String category, boolean visible, List<ProductImageResponse> images,
        List<ProductVariantResponse> variants) {
    /**
     * 엔티티와 사진·옵션 응답으로 생성
     *
     * @param images 표시 순서로 정렬된 사진
     * @param variants 응답에 담을 옵션
     * @return 상품 응답
     */
    public static ProductResponse of(ProductEntity product, List<ProductImageResponse> images,
            List<ProductVariantResponse> variants) {
        return new ProductResponse(product.getId(), product.getCreatedAt(), product.getUpdatedAt(),
                product.getDeletedAt(), product.getName(), product.getDescription(), product.getCategory(),
                product.isVisible(), images, variants);
    }
}

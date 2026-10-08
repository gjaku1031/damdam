package io.github.gjaku1031.damdam.product.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.gjaku1031.damdam.product.domain.ProductImageEntity;

/**
 * 상품 사진 응답
 *
 * @param id ID
 * @param createdAt 생성 시각
 * @param updatedAt 수정 시각
 * @param objectKey 사진 키
 * @param sortOrder 표시 순서
 */
public record ProductImageResponse(UUID id, Instant createdAt, Instant updatedAt, String objectKey, int sortOrder) {
    /**
     * 사진 엔티티로 생성
     *
     * @return 사진 응답
     */
    public static ProductImageResponse from(ProductImageEntity image) {
        return new ProductImageResponse(image.getId(), image.getCreatedAt(), image.getUpdatedAt(), image.getObjectKey(),
                image.getSortOrder());
    }
}

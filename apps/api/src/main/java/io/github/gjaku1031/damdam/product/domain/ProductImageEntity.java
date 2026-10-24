package io.github.gjaku1031.damdam.product.domain;

import java.util.UUID;

import io.github.gjaku1031.damdam.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 사진
 */
@Getter
@Entity
@Table(name = "product_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImageEntity extends BaseEntity {
    /**
     * 상품 ID
     */
    @Column(nullable = false)
    private UUID productId;

    /**
     * 업로드 사진 저장 키({@code product/<uuid>.<ext>})
     */
    @Column(nullable = false, length = 500)
    private String objectKey;

    /**
     * 표시 순서, 0이 대표 사진이며 상품 안에서 유일
     */
    @Column(nullable = false)
    private int sortOrder;

    /**
     * 사진 연결 생성
     *
     * @return 저장 전 사진
     */
    public static ProductImageEntity of(UUID productId, String objectKey, int sortOrder) {
        ProductImageEntity image = new ProductImageEntity();
        image.productId = productId;
        image.objectKey = objectKey;
        image.sortOrder = sortOrder;
        return image;
    }
}

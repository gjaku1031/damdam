package io.github.gjaku1031.damdam.product.domain;

import io.github.gjaku1031.damdam.common.jpa.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매 상품
 *
 * 옵션·사진은 각 엔티티가 상품 ID로 참조
 */
@Getter
@Entity
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductEntity extends SoftDeletableEntity {
    /**
     * 상품명
     */
    @Column(nullable = false)
    private String name;

    /**
     * 설명, 없으면 {@code null}
     */
    @Column(columnDefinition = "text")
    private String description;

    /**
     * 분류명, 없으면 {@code null}
     */
    @Column(length = 50)
    private String category;

    /**
     * 고객 화면 공개 여부
     */
    @Column(nullable = false)
    private boolean visible;

    /**
     * 숨김 상태의 새 상품 생성
     *
     * 이름은 저장 전에 {@link #rename(String)}으로 지정
     *
     * @return 저장 전 상품
     */
    public static ProductEntity draft() {
        return new ProductEntity();
    }

    /**
     * 상품명 변경
     *
     * @param name 앞뒤 공백을 제거한 비어 있지 않은 이름
     */
    public void rename(String name) {
        this.name = name;
    }

    /**
     * 설명 변경
     */
    public void changeDescription(String description) {
        this.description = description;
    }

    /**
     * 분류 변경
     *
     * @param category 분류명, {@code null}이면 분류 해제
     */
    public void changeCategory(String category) {
        this.category = category;
    }

    /**
     * 공개 여부 변경
     */
    public void changeVisibility(boolean visible) {
        this.visible = visible;
    }
}

package io.github.gjaku1031.damdam.product.domain;

import io.github.gjaku1031.damdam.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 분류
 *
 * 상품은 분류 ID가 아닌 분류명을 저장하며 이름 변경·삭제 시 상품의 분류명도 함께 바뀜
 */
@Getter
@Entity
@Table(name = "product_categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductCategoryEntity extends BaseEntity {
    /**
     * 분류명, 유일
     */
    @Column(nullable = false, unique = true, length = 50)
    private String name;

    /**
     * 분류 생성
     *
     * @param name 앞뒤 공백을 제거한 이름
     * @return 저장 전 분류
     */
    public static ProductCategoryEntity named(String name) {
        ProductCategoryEntity category = new ProductCategoryEntity();
        category.name = name;
        return category;
    }

    /**
     * 이름 변경
     */
    public void rename(String name) {
        this.name = name;
    }
}

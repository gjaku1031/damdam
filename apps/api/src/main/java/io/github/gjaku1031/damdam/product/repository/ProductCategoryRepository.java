package io.github.gjaku1031.damdam.product.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.gjaku1031.damdam.product.domain.ProductCategoryEntity;

/**
 * 상품 분류 저장소
 */
public interface ProductCategoryRepository extends JpaRepository<ProductCategoryEntity, UUID> {
    /**
     * 분류명 존재 여부
     *
     * @return 같은 이름의 분류가 있으면 {@code true}
     */
    boolean existsByName(String name);
}

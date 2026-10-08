package io.github.gjaku1031.damdam.product.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import io.github.gjaku1031.damdam.product.domain.ProductImageEntity;

/**
 * 상품 사진 저장소
 */
public interface ProductImageRepository extends JpaRepository<ProductImageEntity, UUID> {
    /**
     * 여러 상품의 사진 조회
     *
     * @param productIds 상품 ID 목록
     * @return 표시 순서 오름차순 사진
     */
    List<ProductImageEntity> findByProductIdInOrderBySortOrderAsc(Collection<UUID> productIds);

    /**
     * 상품의 모든 사진 즉시 삭제
     *
     * 정렬 번호 유일 제약 때문에 새 사진을 넣기 전에 대기 중인 변경을 flush한 뒤 바로 삭제
     */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from ProductImageEntity i where i.productId = :productId")
    void deleteAllOfProduct(UUID productId);
}

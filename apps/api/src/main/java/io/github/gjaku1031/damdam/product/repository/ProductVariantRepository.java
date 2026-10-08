package io.github.gjaku1031.damdam.product.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import io.github.gjaku1031.damdam.product.domain.ProductVariantEntity;
import jakarta.persistence.LockModeType;

/**
 * 상품 옵션 저장소
 */
public interface ProductVariantRepository extends JpaRepository<ProductVariantEntity, UUID> {
    /**
     * 여러 상품의 옵션 조회
     *
     * @param productIds 상품 ID 목록
     * @return 등록 시각·ID 오름차순 옵션
     */
    List<ProductVariantEntity> findByProductIdInOrderByCreatedAtAscIdAsc(Collection<UUID> productIds);

    /**
     * 여러 옵션 잠금 조회
     *
     * 항상 ID 순서로 잠가 동시 주문·수정 간 교착 방지
     *
     * @param ids 옵션 ID 목록
     * @return ID 오름차순 옵션, 없는 ID는 제외
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariantEntity v where v.id in :ids order by v.id")
    List<ProductVariantEntity> findAllForUpdate(Collection<UUID> ids);

    /**
     * 상품의 모든 옵션 잠금 조회
     *
     * @return ID 오름차순 옵션
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariantEntity v where v.productId = :productId order by v.id")
    List<ProductVariantEntity> findByProductForUpdate(UUID productId);

    /**
     * 옵션 하나 잠금 조회
     *
     * @return 옵션, 없으면 빈 값
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ProductVariantEntity v where v.id = :id")
    Optional<ProductVariantEntity> findForUpdate(UUID id);
}

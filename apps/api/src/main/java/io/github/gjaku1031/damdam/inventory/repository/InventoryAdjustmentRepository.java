package io.github.gjaku1031.damdam.inventory.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import io.github.gjaku1031.damdam.inventory.domain.InventoryAdjustmentEntity;

/**
 * 재고 조정 기록 저장소
 */
public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustmentEntity, UUID> {
    /**
     * 옵션의 조정 기록 쪽 조회
     *
     * @param pageable 쪽 번호(0부터)·크기·정렬
     * @return 조정 기록 쪽
     */
    Page<InventoryAdjustmentEntity> findByVariantId(UUID variantId, Pageable pageable);

    /**
     * 옵션의 조정 기록 수
     *
     * @return 기록 건수
     */
    long countByVariantId(UUID variantId);
}

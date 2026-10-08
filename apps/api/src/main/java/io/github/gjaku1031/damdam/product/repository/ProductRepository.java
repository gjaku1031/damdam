package io.github.gjaku1031.damdam.product.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import io.github.gjaku1031.damdam.product.domain.ProductEntity;
import jakarta.persistence.LockModeType;

/**
 * 상품 저장소
 */
public interface ProductRepository extends JpaRepository<ProductEntity, UUID> {
    /**
     * 삭제되지 않은 상품 조회
     *
     * @return 상품, 없거나 삭제됐으면 빈 값
     */
    Optional<ProductEntity> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * 삭제되지 않은 상품 잠금 조회
     *
     * @return 상품, 없거나 삭제됐으면 빈 값
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProductEntity p where p.id = :id and p.deletedAt is null")
    Optional<ProductEntity> findForUpdate(UUID id);
}

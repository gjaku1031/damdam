package io.github.gjaku1031.damdam.cart.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import io.github.gjaku1031.damdam.cart.domain.CartItemEntity;

/**
 * 장바구니 항목 저장소
 */
public interface CartItemRepository extends JpaRepository<CartItemEntity, UUID> {
    /**
     * 장바구니의 항목 조회
     *
     * @return 담은 시각·ID 오름차순 항목
     */
    List<CartItemEntity> findByCartIdOrderByCreatedAtAscIdAsc(UUID cartId);

    /**
     * 장바구니에 담긴 같은 옵션 항목 조회
     *
     * @return 항목, 담지 않았으면 빈 값
     */
    Optional<CartItemEntity> findByCartIdAndVariantId(UUID cartId, UUID variantId);

    /**
     * 본인 장바구니 항목 조회
     *
     * 다른 고객의 항목은 존재하지 않는 것처럼 찾지 못함
     *
     * @return 사용자 장바구니의 항목, 없거나 다른 고객의 항목이면 빈 값
     */
    @Query("select i from CartItemEntity i, CartEntity c where i.id = :id and c.id = i.cartId and c.userId = :userId")
    Optional<CartItemEntity> findOwned(UUID id, UUID userId);
}

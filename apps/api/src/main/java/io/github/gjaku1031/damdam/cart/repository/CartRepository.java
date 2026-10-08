package io.github.gjaku1031.damdam.cart.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.gjaku1031.damdam.cart.domain.CartEntity;

/**
 * 장바구니 저장소
 */
public interface CartRepository extends JpaRepository<CartEntity, UUID> {
    /**
     * 사용자 장바구니 조회
     *
     * @return 장바구니, 아직 없으면 빈 값
     */
    Optional<CartEntity> findByUserId(UUID userId);
}

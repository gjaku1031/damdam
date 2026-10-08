package io.github.gjaku1031.damdam.cart.domain;

import java.util.UUID;

import io.github.gjaku1031.damdam.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원별 서버 장바구니
 */
@Getter
@Entity
@Table(name = "carts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartEntity extends BaseEntity {
    /**
     * 회원 ID, 회원당 하나
     */
    @Column(unique = true)
    private UUID userId;

    /**
     * 회원 장바구니 생성
     *
     * @return 저장 전 장바구니
     */
    public static CartEntity of(UUID userId) {
        CartEntity cart = new CartEntity();
        cart.userId = userId;
        return cart;
    }
}

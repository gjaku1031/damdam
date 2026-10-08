package io.github.gjaku1031.damdam.cart.domain;

import java.time.Instant;
import java.util.UUID;

import io.github.gjaku1031.damdam.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장바구니 항목
 */
@Getter
@Entity
@Table(name = "cart_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItemEntity extends BaseEntity {
    /**
     * 장바구니 ID
     */
    private UUID cartId;

    /**
     * 상품 옵션 ID, 장바구니 안에서 유일
     */
    private UUID variantId;

    /**
     * 수량, 1 이상
     */
    @Column(nullable = false)
    private int quantity = 1;

    /**
     * 담을 때의 판매가(원)
     */
    @Column(nullable = false)
    private int priceSnapshot;

    /**
     * 판매가를 기록한 시각
     */
    @Column(nullable = false)
    private Instant priceCapturedAt;

    /**
     * 항목 생성, 현재 시각으로 가격 기록
     *
     * @return 저장 전 항목
     */
    public static CartItemEntity of(UUID cartId, UUID variantId, int quantity, int priceSnapshot) {
        CartItemEntity item = new CartItemEntity();
        item.cartId = cartId;
        item.variantId = variantId;
        item.quantity = quantity;
        item.priceSnapshot = priceSnapshot;
        item.priceCapturedAt = Instant.now();
        return item;
    }

    /**
     * 수량 변경
     *
     * @throws IllegalArgumentException 수량이 1 미만일 때
     */
    public void updateQuantity(int quantity) {
        if (quantity < 1) throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        this.quantity = quantity;
    }
}

package io.github.gjaku1031.damdam.order.domain;

import java.time.Instant;
import java.util.UUID;

import io.github.gjaku1031.damdam.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 상품
 *
 * 상품·옵션이 바뀌거나 삭제돼도 주문 내역이 유지되도록 주문 시점 값을 스냅샷으로 보존
 */
@Getter
@Entity
@Table(name = "order_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItemEntity extends BaseEntity {
    /**
     * 주문 ID
     */
    private UUID orderId;

    /**
     * 상품 옵션 ID
     */
    private UUID variantId;

    /**
     * 주문 시점 상품명
     */
    @Column(nullable = false)
    private String productNameSnapshot;

    /**
     * 주문 시점 옵션명
     */
    @Column(nullable = false)
    private String variantNameSnapshot;

    /**
     * 주문 시점 대표 사진 키, 사진이 없으면 {@code null}
     */
    private String thumbnailImageUrlSnapshot;

    /**
     * 주문 시점 단가(원)
     */
    @Column(nullable = false)
    private int unitPriceAmount;

    /**
     * 수량
     */
    @Column(nullable = false)
    private int quantity = 1;

    /**
     * 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderItemStatus status = OrderItemStatus.PENDING_PAYMENT;

    /**
     * 결제 완료 시각
     */
    private Instant paidAt;

    /**
     * 배송 시작 시각
     */
    private Instant shippedAt;

    /**
     * 취소 시각
     */
    private Instant canceledAt;

    /**
     * 환불 시각
     */
    private Instant refundedAt;

    /**
     * 주문 시점 값으로 주문 상품 생성
     *
     * @param thumbnail 대표 사진 키, 없으면 {@code null}
     * @return 저장 전 주문 상품
     */
    public static OrderItemEntity snapshot(UUID orderId, UUID variantId, String productName, String variantName,
            String thumbnail, int unitPriceAmount, int quantity) {
        OrderItemEntity item = new OrderItemEntity();
        item.orderId = orderId;
        item.variantId = variantId;
        item.productNameSnapshot = productName;
        item.variantNameSnapshot = variantName;
        item.thumbnailImageUrlSnapshot = thumbnail;
        item.unitPriceAmount = unitPriceAmount;
        item.quantity = quantity;
        return item;
    }

    /**
     * 주문 상태 변경에 따른 상태 변경
     */
    public void changeStatus(OrderItemStatus status) {
        this.status = status;
    }


}

package io.github.gjaku1031.damdam.order.domain;

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
 * 주문 상태 변경 이력
 */
@Getter
@Entity
@Table(name = "order_status_histories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderStatusHistoryEntity extends BaseEntity {
    /**
     * 주문 ID
     */
    private UUID orderId;

    /**
     * 이전 상태, 주문 생성이면 {@code null}
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private OrderStatus fromStatus;

    /**
     * 변경 후 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus toStatus;

    /**
     * 변경 주체
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChangeActorType changedBy;

    /**
     * 사유, 없으면 {@code null}
     */
    @Column(columnDefinition = "text")
    private String reason;

    /**
     * 이력 생성
     *
     * @return 저장 전 이력
     */
    public static OrderStatusHistoryEntity of(UUID orderId, OrderStatus fromStatus, OrderStatus toStatus,
            ChangeActorType changedBy, String reason) {
        OrderStatusHistoryEntity history = new OrderStatusHistoryEntity();
        history.orderId = orderId;
        history.fromStatus = fromStatus;
        history.toStatus = toStatus;
        history.changedBy = changedBy;
        history.reason = reason;
        return history;
    }
}

package io.github.gjaku1031.damdam.inventory.domain;

import java.time.Duration;
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
 * 결제 완료 전까지 주문이 임시로 확보한 재고
 *
 * TTL이 지나면 스케줄러가 만료 처리
 */
@Getter
@Entity
@Table(name = "inventory_reservations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryReservationEntity extends BaseEntity {
    /**
     * 상품 옵션 ID
     */
    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    /**
     * 주문 ID
     */
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    /**
     * 예약 수량
     */
    @Column(nullable = false)
    private int quantity;

    /**
     * 예약 시각
     */
    @Column(nullable = false)
    private Instant reservedAt;

    /**
     * 만료 시각
     */
    @Column(nullable = false)
    private Instant expiresAt;

    /**
     * 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InventoryReservationStatus status = InventoryReservationStatus.ACTIVE;

    /**
     * 현재 시각부터 TTL 동안 유효한 예약 생성
     *
     * @return 저장 전 예약
     */
    public static InventoryReservationEntity reserve(UUID variantId, UUID orderId, int quantity, Duration ttl) {
        InventoryReservationEntity reservation = new InventoryReservationEntity();
        reservation.variantId = variantId;
        reservation.orderId = orderId;
        reservation.quantity = quantity;
        reservation.reservedAt = Instant.now();
        reservation.expiresAt = reservation.reservedAt.plus(ttl);
        return reservation;
    }

    /**
     * 결제 완료로 소진
     */
    public void consume() {
        status = InventoryReservationStatus.CONSUMED;
    }

    /**
     * 만료
     */
    public void expire() {
        status = InventoryReservationStatus.EXPIRED;
    }

    /**
     * 취소
     */
    public void cancel() {
        status = InventoryReservationStatus.CANCELED;
    }
}

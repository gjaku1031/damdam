package io.github.gjaku1031.damdam.inventory.domain;

/**
 * 재고 예약 상태
 */
public enum InventoryReservationStatus {
    /**
     * 결제 대기 중 확보
     */
    ACTIVE,

    /**
     * 결제 완료로 소진
     */
    CONSUMED,

    /**
     * TTL 경과로 만료
     */
    EXPIRED,

    /**
     * 주문 취소 등으로 해제
     */
    CANCELED
}

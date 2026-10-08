package io.github.gjaku1031.damdam.order.domain;

/**
 * 주문 상품 상태
 */
public enum OrderItemStatus {
    /**
     * 결제 대기
     */
    PENDING_PAYMENT,

    /**
     * 결제 완료
     */
    PAID,

    /**
     * 배송 중
     */
    SHIPPED,

    /**
     * 배송 완료
     */
    DELIVERED,

    /**
     * 취소
     */
    CANCELED,

    /**
     * 환불
     */
    REFUNDED
}

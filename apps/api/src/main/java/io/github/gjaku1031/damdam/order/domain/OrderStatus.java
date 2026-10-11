package io.github.gjaku1031.damdam.order.domain;

/**
 * 주문 상태
 */
public enum OrderStatus {
    /**
     * 결제 대기
     */
    PENDING_PAYMENT,

    /**
     * 결제 완료
     */
    PAID,

    /**
     * 상품 준비·배송 중
     */
    PROCESSING,

    /**
     * 배송 완료
     */
    COMPLETED,

    /**
     * 취소
     */
    CANCELED,

    /**
     * 전액 환불
     */
    FULLY_REFUNDED
}

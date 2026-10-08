package io.github.gjaku1031.damdam.order.domain;

/**
 * 주문 결제 수단
 *
 * 무통장 입금만 지원
 */
public enum OrderPaymentMethod {
    /**
     * 무통장 입금, 운영자가 입금 확인
     */
    BANK_TRANSFER
}

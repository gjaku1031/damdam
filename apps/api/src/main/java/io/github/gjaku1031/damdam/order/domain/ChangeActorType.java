package io.github.gjaku1031.damdam.order.domain;

/**
 * 상태를 바꾼 주체
 */
public enum ChangeActorType {
    /**
     * 고객
     */
    CUSTOMER,

    /**
     * 운영자
     */
    ADMIN,

    /**
     * 결제 연동 등 시스템
     */
    SYSTEM
}

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
 * 주문
 *
 * 금액은 주문 시점의 서버 가격으로 확정, 상태는 {@link OrderTransitions}가 허용한 전이만 수행
 */
@Getter
@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderEntity extends BaseEntity {
    /**
     * 주문번호({@code ORD-<UTC 시각>-<무작위 6자>})
     */
    @Column(nullable = false, unique = true, length = 40)
    private String orderNo;

    /**
     * 주문자 ID
     */
    private UUID userId;

    /**
     * 주문 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    /**
     * 상품 합계(원)
     */
    @Column(nullable = false)
    private int totalAmount;

    /**
     * 배송비(원)
     */
    @Column(nullable = false)
    private int shippingCost;

    /**
     * 결제 금액(원), 상품 합계 + 배송비
     */
    @Column(nullable = false)
    private int finalPaymentAmount;

    /**
     * 환불 누적 금액(원)
     */
    @Column(nullable = false)
    private int refundedAmount;

    /**
     * 통화 코드
     */
    @Column(nullable = false, length = 3)
    private String currency = "KRW";

    /**
     * 결제 수단
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderPaymentMethod paymentMethod = OrderPaymentMethod.BANK_TRANSFER;

    /**
     * 주문자 이름
     */
    @Column(nullable = false)
    private String buyerName;

    /**
     * 주문자 연락처
     */
    @Column(nullable = false)
    private String buyerPhone;

    /**
     * 받는 사람
     */
    @Column(nullable = false)
    private String shipName;

    /**
     * 받는 사람 연락처
     */
    @Column(nullable = false)
    private String shipPhone;

    /**
     * 우편번호
     */
    @Column(nullable = false)
    private String shipPostalCode;

    /**
     * 기본 주소
     */
    @Column(nullable = false)
    private String shipAddress1;

    /**
     * 상세 주소, 없으면 빈 문자열
     */
    @Column(nullable = false)
    private String shipAddress2;

    /**
     * 배송 메모, 없으면 빈 문자열
     */
    @Column(nullable = false, length = 200)
    private String shipMemo = "";

    /**
     * 입금 기한, 지나면 결제 대기 주문을 자동 취소. 기한 도입 전 주문이면 {@code null}
     */
    private Instant paymentDueAt;

    /**
     * 결제 완료 시각
     */
    private Instant paidAt;

    /**
     * 취소 시각
     */
    private Instant canceledAt;

    /**
     * 전액 환불 시각
     */
    private Instant refundedAt;

    /**
     * 결제 대기 주문 생성
     *
     * @param totalAmount 서버 가격으로 계산한 상품 합계, 배송비 0원
     * @param paymentDueAt 입금 기한
     * @return 저장 전 주문
     */
    public static OrderEntity place(String orderNo, UUID userId, OrderPaymentMethod paymentMethod, int totalAmount,
            OrderContact contact, Instant paymentDueAt) {
        OrderEntity order = new OrderEntity();
        order.orderNo = orderNo;
        order.userId = userId;
        order.paymentMethod = paymentMethod;
        order.totalAmount = totalAmount;
        order.shippingCost = 0;
        order.finalPaymentAmount = totalAmount;
        order.buyerName = contact.buyerName();
        order.buyerPhone = contact.buyerPhone();
        order.shipName = contact.shipName();
        order.shipPhone = contact.shipPhone();
        order.shipPostalCode = contact.shipPostalCode();
        order.shipAddress1 = contact.shipAddress1();
        order.shipAddress2 = contact.shipAddress2() == null ? "" : contact.shipAddress2();
        order.shipMemo = contact.shipMemo() == null ? "" : contact.shipMemo();
        order.paymentDueAt = paymentDueAt;
        return order;
    }

    /**
     * 주문자 여부
     *
     * @return 주문자 ID가 같으면 {@code true}
     */
    public boolean isOwnedBy(UUID userId) {
        return this.userId != null && this.userId.equals(userId);
    }

    /**
     * 입금 기한 경과 여부
     *
     * @param now 기준 시각
     * @return 결제 대기 중이고 기한이 기준 시각 이전이거나 같으면 {@code true}, 기한이 없는 주문은 {@code false}
     */
    public boolean isPaymentOverdue(Instant now) {
        return status == OrderStatus.PENDING_PAYMENT && paymentDueAt != null && !paymentDueAt.isAfter(now);
    }

    /**
     * 입금 기한 변경
     *
     * @throws IllegalStateException 결제 대기 주문이 아닐 때
     */
    public void changePaymentDue(Instant paymentDueAt) {
        if (status != OrderStatus.PENDING_PAYMENT) throw new IllegalStateException("결제 대기 주문이 아닙니다.");
        this.paymentDueAt = paymentDueAt;
    }

    /**
     * 결제 완료 처리
     */
    public void markPaid(Instant paidAt) {
        status = OrderStatus.PAID;
        this.paidAt = paidAt;
    }

    /**
     * 취소 처리, 재고 복원은 호출자가 수행
     */
    public void cancel(Instant canceledAt) {
        status = OrderStatus.CANCELED;
        this.canceledAt = canceledAt;
    }

    /**
     * 운영자 전액 환불 처리, 결제 금액 전체를 환불 금액으로 기록
     */
    public void refundFully(Instant refundedAt) {
        status = OrderStatus.FULLY_REFUNDED;
        this.refundedAt = refundedAt;
        refundedAmount = finalPaymentAmount;
    }

    /**
     * 시각·금액 기록 없이 상태만 변경(준비 중, 배송 완료, 일부 환불)
     */
    public void moveTo(OrderStatus status) {
        this.status = status;
    }

}

package io.github.gjaku1031.damdam.order.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 주문 상태 전이 규칙
 */
final class OrderTransitionsTest {
    /**
     * 결제 대기에서는 입금 확인과 취소만 가능
     */
    @Test
    void pendingPaymentCanOnlyBePaidOrCanceled() {
        assertThat(OrderTransitions.allowed(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID)).isTrue();
        assertThat(OrderTransitions.allowed(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELED)).isTrue();
        assertThat(OrderTransitions.allowed(OrderStatus.PENDING_PAYMENT, OrderStatus.PROCESSING)).isFalse();
        assertThat(OrderTransitions.allowed(OrderStatus.PENDING_PAYMENT, OrderStatus.COMPLETED)).isFalse();
        assertThat(OrderTransitions.allowed(OrderStatus.PENDING_PAYMENT, OrderStatus.FULLY_REFUNDED)).isFalse();
    }

    /**
     * 돈을 받은 뒤에는 전액 환불이 가능하고, 발송을 시작한 뒤에는 취소할 수 없음
     */
    @Test
    void paidOrdersCanBeRefundedButShippedOrdersCannotBeCanceled() {
        assertThat(OrderTransitions.allowed(OrderStatus.PAID, OrderStatus.FULLY_REFUNDED)).isTrue();
        assertThat(OrderTransitions.allowed(OrderStatus.PAID, OrderStatus.CANCELED)).isTrue();
        assertThat(OrderTransitions.allowed(OrderStatus.PROCESSING, OrderStatus.FULLY_REFUNDED)).isTrue();
        assertThat(OrderTransitions.allowed(OrderStatus.PROCESSING, OrderStatus.CANCELED)).isFalse();
        assertThat(OrderTransitions.allowed(OrderStatus.COMPLETED, OrderStatus.FULLY_REFUNDED)).isTrue();
        assertThat(OrderTransitions.allowed(OrderStatus.COMPLETED, OrderStatus.CANCELED)).isFalse();
    }

    /**
     * 취소와 전액 환불은 어떤 상태로도 바꿀 수 없는 종료 상태, 그래서 재고가 두 번 복원되지 않음
     */
    @Test
    void canceledAndRefundedAreTerminal() {
        for (OrderStatus to : OrderStatus.values()) {
            assertThat(OrderTransitions.allowed(OrderStatus.CANCELED, to)).as("취소 → %s", to).isFalse();
            assertThat(OrderTransitions.allowed(OrderStatus.FULLY_REFUNDED, to)).as("전액 환불 → %s", to).isFalse();
        }
    }

    /**
     * 같은 상태로의 전이는 어떤 상태에서도 거부
     */
    @Test
    void sameStatusIsNeverAllowed() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(OrderTransitions.allowed(status, status)).as("%s → %s", status, status).isFalse();
        }
    }

    /**
     * 주문 상태마다 주문 상품 상태가 따라 바뀌고, 결제 대기에는 대응하는 상품 상태가 없음
     */
    @Test
    void mapsOrderStatusToItemStatus() {
        assertThat(OrderTransitions.itemStatus(OrderStatus.PAID)).contains(OrderItemStatus.PAID);
        assertThat(OrderTransitions.itemStatus(OrderStatus.PROCESSING)).contains(OrderItemStatus.SHIPPED);
        assertThat(OrderTransitions.itemStatus(OrderStatus.COMPLETED)).contains(OrderItemStatus.DELIVERED);
        assertThat(OrderTransitions.itemStatus(OrderStatus.CANCELED)).contains(OrderItemStatus.CANCELED);
        assertThat(OrderTransitions.itemStatus(OrderStatus.FULLY_REFUNDED)).contains(OrderItemStatus.REFUNDED);
        assertThat(OrderTransitions.itemStatus(OrderStatus.PENDING_PAYMENT)).isEmpty();
    }
}

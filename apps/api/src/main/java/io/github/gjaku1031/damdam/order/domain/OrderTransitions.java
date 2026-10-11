package io.github.gjaku1031.damdam.order.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 허용된 주문 상태 전이와 상태별 주문 상품 상태
 */
public final class OrderTransitions {
    /**
     * 상태별 다음으로 허용되는 상태, 종료 상태는 빈 집합
     */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(Map.of(
            OrderStatus.PENDING_PAYMENT, Set.of(OrderStatus.PAID, OrderStatus.CANCELED),
            OrderStatus.PAID, Set.of(OrderStatus.PROCESSING, OrderStatus.CANCELED, OrderStatus.FULLY_REFUNDED),
            OrderStatus.PROCESSING, Set.of(OrderStatus.COMPLETED, OrderStatus.FULLY_REFUNDED),
            OrderStatus.COMPLETED, Set.of(OrderStatus.FULLY_REFUNDED),
            OrderStatus.CANCELED, Set.of(),
            OrderStatus.FULLY_REFUNDED, Set.of()));

    /**
     * 주문 상태 전이 시 주문 상품에 적용할 상태, 전이 대상이 아닌 결제 대기는 항목 없음
     */
    private static final Map<OrderStatus, OrderItemStatus> ITEM_STATUS = new EnumMap<>(Map.of(
            OrderStatus.PAID, OrderItemStatus.PAID,
            OrderStatus.PROCESSING, OrderItemStatus.SHIPPED,
            OrderStatus.COMPLETED, OrderItemStatus.DELIVERED,
            OrderStatus.CANCELED, OrderItemStatus.CANCELED,
            OrderStatus.FULLY_REFUNDED, OrderItemStatus.REFUNDED));

    /**
     * 인스턴스 생성 방지
     */
    private OrderTransitions() {}

    /**
     * 상태 전이 허용 여부
     *
     * @param from 현재 상태
     * @param to 바꿀 상태
     * @return 허용된 전이면 {@code true}
     */
    public static boolean allowed(OrderStatus from, OrderStatus to) {
        return ALLOWED.get(from).contains(to);
    }

    /**
     * 주문 상태에 대응하는 주문 상품 상태
     *
     * @param status 전이 후 주문 상태
     * @return 주문 상품 상태, 대응하는 상태가 없으면 빈 값
     */
    public static Optional<OrderItemStatus> itemStatus(OrderStatus status) {
        return Optional.ofNullable(ITEM_STATUS.get(status));
    }
}

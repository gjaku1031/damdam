package io.github.gjaku1031.damdam.order.dto;

import io.github.gjaku1031.damdam.order.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 운영자 주문 상태 변경 요청
 *
 * @param status 변경할 상태
 * @param reason 변경 사유, 생략 가능
 * @param restock 전액 환불 시 주문 수량을 재고로 되돌릴지 여부, 생략하면 되돌리지 않음. 전액 환불이 아닌데 {@code true}면 400
 */
public record UpdateOrderStatusRequest(@NotNull OrderStatus status, String reason, Boolean restock) {
    /**
     * 재입고 선택 여부
     *
     * @return {@code restock}이 {@code true}일 때만 {@code true}
     */
    public boolean restockRequested() {
        return Boolean.TRUE.equals(restock);
    }
}

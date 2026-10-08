package io.github.gjaku1031.damdam.order.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.gjaku1031.damdam.order.domain.ChangeActorType;
import io.github.gjaku1031.damdam.order.domain.OrderStatus;
import io.github.gjaku1031.damdam.order.domain.OrderStatusHistoryEntity;

/**
 * 주문 상태 변경 이력 응답
 *
 * @param id 이력 ID
 * @param createdAt 생성 시각
 * @param updatedAt 수정 시각
 * @param fromStatus 변경 전 상태, 최초 생성 이력이면 {@code null}
 * @param toStatus 변경 후 상태
 * @param changedBy 변경 주체
 * @param reason 변경 사유, 없으면 {@code null}
 */
public record OrderStatusHistoryResponse(UUID id, Instant createdAt, Instant updatedAt, OrderStatus fromStatus,
        OrderStatus toStatus, ChangeActorType changedBy, String reason) {
    /**
     * 이력 엔티티 변환
     *
     * @param history 주문 상태 이력 엔티티
     * @return 이력 응답
     */
    public static OrderStatusHistoryResponse from(OrderStatusHistoryEntity history) {
        return new OrderStatusHistoryResponse(history.getId(), history.getCreatedAt(), history.getUpdatedAt(),
                history.getFromStatus(), history.getToStatus(), history.getChangedBy(), history.getReason());
    }
}

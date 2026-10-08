package io.github.gjaku1031.damdam.order.dto;

import io.github.gjaku1031.damdam.order.domain.OrderStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 운영자 주문 상태 변경 요청
 *
 * @param status 변경할 상태
 * @param reason 변경 사유, 생략 가능
 */
public record UpdateOrderStatusRequest(@NotNull OrderStatus status, String reason) {}

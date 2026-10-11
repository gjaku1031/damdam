package io.github.gjaku1031.damdam.order.dto;

import java.util.List;
import java.util.UUID;

import io.github.gjaku1031.damdam.order.dto.CreateOrderRequest.Item;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 운영자 대리 주문 요청
 *
 * 주문자·연락처·배송지는 원 주문에서 그대로 가져오므로 받지 않음
 *
 * @param sourceOrderId 주문자와 배송지를 가져올 원 주문 ID
 * @param items 주문 상품, 1~30개
 * @param paid 이미 받은 돈으로 처리할지 여부, {@code true}면 입금 확인 상태로 생성. 생략하면 결제 대기
 */
public record CreateProxyOrderRequest(
        @NotNull UUID sourceOrderId,
        @NotNull @Size(min = 1, max = 30) List<@NotNull @Valid Item> items,
        Boolean paid) {
    /**
     * 입금 확인 상태로 만들지 여부
     *
     * @return {@code paid}가 {@code true}일 때만 {@code true}
     */
    public boolean paidRequested() {
        return Boolean.TRUE.equals(paid);
    }
}

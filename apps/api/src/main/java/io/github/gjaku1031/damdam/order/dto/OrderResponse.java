package io.github.gjaku1031.damdam.order.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.github.gjaku1031.damdam.order.domain.OrderEntity;
import io.github.gjaku1031.damdam.order.domain.OrderPaymentMethod;
import io.github.gjaku1031.damdam.order.domain.OrderStatus;
import io.github.gjaku1031.damdam.user.dto.UserResponse;

/**
 * 주문 응답
 *
 * 함께 조회한 관계만 담고, 조회하지 않은 관계({@code items}·{@code statusHistories}·{@code user})는 키 생략
 *
 * @param id 주문 ID
 * @param createdAt 생성 시각
 * @param updatedAt 수정 시각
 * @param orderNo 주문번호
 * @param status 주문 상태
 * @param totalAmount 상품 합계 금액
 * @param shippingCost 배송비
 * @param finalPaymentAmount 최종 결제 금액
 * @param refundedAmount 누적 환불 금액
 * @param currency 통화 코드
 * @param paymentMethod 결제 수단
 * @param buyerName 주문자 이름
 * @param buyerPhone 주문자 연락처
 * @param shipName 받는 사람
 * @param shipPhone 받는 사람 연락처
 * @param shipPostalCode 우편번호
 * @param shipAddress1 기본 주소
 * @param shipAddress2 상세 주소
 * @param shipMemo 배송 메모, 없으면 빈 문자열
 * @param paidAt 결제 시각, 미결제면 {@code null}
 * @param canceledAt 취소 시각, 취소 전이면 {@code null}
 * @param refundedAt 환불 시각, 환불 전이면 {@code null}
 * @param items 주문 상품 목록, 조회하지 않았으면 {@code null}(키 생략)
 * @param statusHistories 상태 변경 이력, 조회하지 않았으면 {@code null}(키 생략)
 * @param user 주문 회원, 조회하지 않았거나 삭제된 회원이면 {@code null}(키 생략)
 */
public record OrderResponse(UUID id, Instant createdAt, Instant updatedAt, String orderNo, OrderStatus status,
        int totalAmount, int shippingCost, int finalPaymentAmount, int refundedAmount, String currency,
        OrderPaymentMethod paymentMethod, String buyerName, String buyerPhone, String shipName, String shipPhone,
        String shipPostalCode, String shipAddress1, String shipAddress2, String shipMemo, Instant paidAt, Instant canceledAt,
        Instant refundedAt,
        @JsonInclude(Include.NON_NULL) List<OrderItemResponse> items,
        @JsonInclude(Include.NON_NULL) List<OrderStatusHistoryResponse> statusHistories,
        @JsonInclude(Include.NON_NULL) UserResponse user) {
    /**
     * 주문 행만 담은 응답 생성
     *
     * @param order 주문 엔티티
     * @return 관계 키를 모두 생략한 응답
     */
    public static OrderResponse of(OrderEntity order) {
        return of(order, null, null, null);
    }

    /**
     * 함께 조회한 관계를 포함한 응답 생성
     *
     * @param order 주문 엔티티
     * @param items 주문 상품 목록, {@code null}이면 키 생략
     * @param statusHistories 상태 변경 이력, {@code null}이면 키 생략
     * @param user 주문 회원, {@code null}이면 키 생략
     * @return 주문 응답
     */
    public static OrderResponse of(OrderEntity order, List<OrderItemResponse> items,
            List<OrderStatusHistoryResponse> statusHistories, UserResponse user) {
        return new OrderResponse(order.getId(), order.getCreatedAt(), order.getUpdatedAt(), order.getOrderNo(),
                order.getStatus(), order.getTotalAmount(), order.getShippingCost(), order.getFinalPaymentAmount(),
                order.getRefundedAmount(), order.getCurrency(), order.getPaymentMethod(), order.getBuyerName(),
                order.getBuyerPhone(), order.getShipName(), order.getShipPhone(), order.getShipPostalCode(),
                order.getShipAddress1(), order.getShipAddress2(), order.getShipMemo(), order.getPaidAt(), order.getCanceledAt(),
                order.getRefundedAt(), items, statusHistories, user);
    }
}

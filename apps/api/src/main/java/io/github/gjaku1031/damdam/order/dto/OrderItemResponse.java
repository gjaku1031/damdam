package io.github.gjaku1031.damdam.order.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.github.gjaku1031.damdam.order.domain.OrderItemEntity;
import io.github.gjaku1031.damdam.order.domain.OrderItemStatus;

/**
 * 주문 상품 스냅샷 응답
 *
 * 상세 조회에서는 당시 옵션({@code variant})을 함께 담음
 *
 * @param id 주문 상품 ID
 * @param createdAt 생성 시각
 * @param updatedAt 수정 시각
 * @param productNameSnapshot 주문 시점 상품명
 * @param variantNameSnapshot 주문 시점 옵션명
 * @param thumbnailImageUrlSnapshot 주문 시점 대표 이미지 URL
 * @param unitPriceAmount 주문 시점 단가
 * @param quantity 수량
 * @param status 주문 상품 상태
 * @param paidAt 결제 시각, 미결제면 {@code null}
 * @param shippedAt 발송 시각, 발송 전이면 {@code null}
 * @param canceledAt 취소 시각, 취소 전이면 {@code null}
 * @param refundedAt 환불 시각, 환불 전이면 {@code null}
 * @param variant 당시 옵션 정보, 상세 조회가 아니면 {@code null}(키 생략)
 */
public record OrderItemResponse(UUID id, Instant createdAt, Instant updatedAt, String productNameSnapshot,
        String variantNameSnapshot, String thumbnailImageUrlSnapshot, int unitPriceAmount, int quantity,
        OrderItemStatus status, Instant paidAt, Instant shippedAt, Instant canceledAt, Instant refundedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Object variant) {
    /**
     * 옵션 정보 없는 응답 생성
     *
     * @param item 주문 상품 엔티티
     * @return {@code variant} 키를 생략한 응답
     */
    public static OrderItemResponse from(OrderItemEntity item) {
        return from(item, null);
    }

    /**
     * 옵션 정보를 포함한 응답 생성
     *
     * @param item 주문 상품 엔티티
     * @param variant 당시 옵션 정보, {@code null}이면 키 생략
     * @return 주문 상품 응답
     */
    public static OrderItemResponse from(OrderItemEntity item, Object variant) {
        return new OrderItemResponse(item.getId(), item.getCreatedAt(), item.getUpdatedAt(),
                item.getProductNameSnapshot(), item.getVariantNameSnapshot(), item.getThumbnailImageUrlSnapshot(),
                item.getUnitPriceAmount(), item.getQuantity(), item.getStatus(), item.getPaidAt(), item.getShippedAt(),
                item.getCanceledAt(), item.getRefundedAt(), variant);
    }
}

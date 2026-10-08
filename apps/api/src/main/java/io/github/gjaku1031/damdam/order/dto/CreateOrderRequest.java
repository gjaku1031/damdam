package io.github.gjaku1031.damdam.order.dto;

import java.util.List;
import java.util.UUID;

import io.github.gjaku1031.damdam.order.domain.OrderContact;
import io.github.gjaku1031.damdam.order.domain.OrderPaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 주문 생성 요청
 *
 * 가격·회원 정보는 받지 않고 서버가 현재 값으로 결정, 선언하지 않은 필드는 400
 *
 * @param items 주문할 옵션과 수량, 1~30개
 * @param buyerName 주문자 이름
 * @param buyerPhone 주문자 연락처
 * @param shipName 받는 사람
 * @param shipPhone 받는 사람 연락처
 * @param shipPostalCode 우편번호
 * @param shipAddress1 기본 주소
 * @param shipAddress2 상세 주소, 생략 가능하지만 빈 문자열은 거부
 * @param paymentMethod 결제 수단, 생략하면 무통장 입금
 * @param shipMemo 배송 메모, 최대 200자, 생략하면 빈 문자열
 */
public record CreateOrderRequest(
        @NotNull @Size(min = 1, max = 30) List<@NotNull @Valid Item> items,
        @NotEmpty @Size(max = 100) String buyerName,
        @NotEmpty @Size(max = 100) String buyerPhone,
        @NotEmpty @Size(max = 200) String shipName,
        @NotEmpty @Size(max = 200) String shipPhone,
        @NotEmpty @Size(max = 200) String shipPostalCode,
        @NotEmpty @Size(max = 200) String shipAddress1,
        @Size(min = 1, max = 200) String shipAddress2,
        OrderPaymentMethod paymentMethod,
        @Size(max = 200) String shipMemo) {
    /**
     * 주문 항목
     *
     * @param variantId 상품 옵션 ID
     * @param quantity 수량, 1~999
     */
    public record Item(@NotNull UUID variantId, @NotNull @Min(1) @Max(999) Integer quantity) {}

    /**
     * 결제 수단, 생략 시 무통장 입금
     *
     * @return 결제 수단
     */
    public OrderPaymentMethod paymentMethodOrDefault() {
        return paymentMethod == null ? OrderPaymentMethod.BANK_TRANSFER : paymentMethod;
    }

    /**
     * 연락처·배송지 값 객체 변환
     *
     * @return 주문 엔티티 생성용 연락처
     */
    public OrderContact contact() {
        return new OrderContact(buyerName, buyerPhone, shipName, shipPhone, shipPostalCode, shipAddress1,
                shipAddress2 == null ? "" : shipAddress2, shipMemo == null ? "" : shipMemo.trim());
    }
}

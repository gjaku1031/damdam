package io.github.gjaku1031.damdam.order.controller;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.gjaku1031.damdam.common.auth.AdminOnly;
import io.github.gjaku1031.damdam.common.auth.CurrentUser;
import io.github.gjaku1031.damdam.common.error.ApiException;
import io.github.gjaku1031.damdam.common.web.Paging;
import io.github.gjaku1031.damdam.order.domain.OrderStatus;
import io.github.gjaku1031.damdam.order.dto.ChangePaymentDueRequest;
import io.github.gjaku1031.damdam.order.dto.CreateProxyOrderRequest;
import io.github.gjaku1031.damdam.order.dto.OrderPage;
import io.github.gjaku1031.damdam.order.dto.OrderResponse;
import io.github.gjaku1031.damdam.order.dto.UpdateOrderStatusRequest;
import io.github.gjaku1031.damdam.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 주문 조회·대리 주문·상태 변경·입금 기한 변경 API
 */
@RestController
@AdminOnly
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
public final class AdminOrderController {
    /**
     * 주문 서비스
     */
    private final OrderService orders;

    /**
     * 전체 주문 목록
     *
     * @param page 페이지 번호, 생략하거나 숫자가 아니면 1
     * @param limit 페이지 크기, 생략하거나 숫자가 아니면 20, 1~100으로 보정
     * @param status 주문 상태 이름({@code PENDING_PAYMENT} 등), 생략 시 전체
     * @param search 주문번호·주문자·받는 사람·주문자 연락처·회원 아이디 부분 일치(대소문자 무시), 생략 시 전체
     * @return 최근 주문 순, 주문 상품·주문자 포함
     * @throws ApiException 알 수 없는 상태 이름일 때(400, {@code fields.status})
     */
    @GetMapping
    public OrderPage list(@RequestParam(required = false) String page, @RequestParam(required = false) String limit,
            @RequestParam(defaultValue = "") String status, @RequestParam(defaultValue = "") String search) {
        return orders.listAll(Paging.of(page, limit, 20), parseStatus(status), search.trim());
    }

    /**
     * 주문 상세
     *
     * @return 주문 상품(당시 옵션 포함)·상태 이력·주문자
     */
    @GetMapping("/{id}")
    public OrderResponse detail(@PathVariable UUID id) {
        return orders.detail(id);
    }

    /**
     * 고객 대신 주문 생성
     *
     * 주문자·배송지는 원 주문에서 가져오고, 상품 가격·재고 규칙은 고객 주문과 같음
     * 운영 기록은 서비스가 주문번호·회원·원 주문과 함께 남김
     *
     * @return 생성한 주문
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateProxyOrderRequest body, CurrentUser user) {
        return orders.createForCustomer(user.id(), body);
    }

    /**
     * 운영자 주문 상태 변경
     *
     * 취소는 재고를 되돌리고, 전액 환불은 {@code restock}을 선택했을 때만 되돌림
     *
     * @return 변경한 주문
     */
    @PatchMapping("/{id}/status")
    public OrderResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateOrderStatusRequest body,
            CurrentUser user) {
        return orders.changeStatus(id, body.status(), body.reason(), user.id(), body.restockRequested());
    }

    /**
     * 결제 대기 주문의 입금 기한 변경
     *
     * 운영 기록은 서비스가 이전·새 기한과 함께 남김
     *
     * @return 변경한 주문
     */
    @PatchMapping("/{id}/payment-due")
    public OrderResponse changePaymentDue(@PathVariable UUID id, @Valid @RequestBody ChangePaymentDueRequest body,
            CurrentUser user) {
        return orders.changePaymentDue(id, body.dueAt(), user.id());
    }

    /**
     * 상태 필터 해석
     *
     * @param raw 쿼리 문자열 값, 대소문자 무시
     * @return 주문 상태, 비어 있으면 {@code null}(전체)
     * @throws ApiException 알 수 없는 상태 이름일 때(400, {@code fields.status})
     */
    private static OrderStatus parseStatus(String raw) {
        if (raw.isBlank()) return null;
        try {
            return OrderStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "입력 내용을 확인해주세요.",
                    Map.of("status", "status must be an order status"));
        }
    }
}

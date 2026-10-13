package io.github.gjaku1031.damdam.order.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.gjaku1031.damdam.common.auth.CurrentUser;
import io.github.gjaku1031.damdam.common.auth.LoginRequired;
import io.github.gjaku1031.damdam.common.web.Paging;
import io.github.gjaku1031.damdam.order.dto.CreateOrderRequest;
import io.github.gjaku1031.damdam.order.dto.OrderPage;
import io.github.gjaku1031.damdam.order.dto.OrderResponse;
import io.github.gjaku1031.damdam.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 회원 주문 생성·조회·취소 API
 */
@RestController
@LoginRequired
@RequestMapping("/orders")
@RequiredArgsConstructor
public final class OrderController {
    /**
     * 주문 서비스
     */
    private final OrderService orders;

    /**
     * 주문 생성
     *
     * 회원 한 명의 결제 대기 주문이 5건이면 409로 거부
     *
     * @return 생성한 주문
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest body, CurrentUser user) {
        return orders.create(user.id(), body);
    }

    /**
     * 본인 주문 목록
     *
     * @param page 페이지 번호, 생략하거나 숫자가 아니면 1
     * @param limit 페이지 크기, 생략하거나 숫자가 아니면 20, 1~100으로 보정
     * @return 최근 주문 순, 주문 상품 포함
     */
    @GetMapping("/me")
    public OrderPage mine(@RequestParam(required = false) String page, @RequestParam(required = false) String limit,
            CurrentUser user) {
        return orders.listForCustomer(user.id(), Paging.of(page, limit, 20));
    }

    /**
     * 본인 주문 취소
     *
     * @return 취소한 주문
     */
    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse cancel(@PathVariable UUID id, CurrentUser user) {
        return orders.cancelForCustomer(id, user.id());
    }

    /**
     * 본인 주문 상세
     *
     * @return 주문 상품(당시 옵션 포함)·상태 이력·주문자
     */
    @GetMapping("/{id}")
    public OrderResponse detail(@PathVariable UUID id, CurrentUser user) {
        return orders.detailForCustomer(id, user.id());
    }
}

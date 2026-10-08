package io.github.gjaku1031.damdam.order.dto;

import java.util.List;

/**
 * 주문 목록 응답 {@code {orders, total, page, limit}}
 *
 * @param orders 현재 페이지 주문
 * @param total 전체 주문 수
 * @param page 페이지 번호
 * @param limit 페이지 크기
 */
public record OrderPage(List<OrderResponse> orders, long total, int page, int limit) {}

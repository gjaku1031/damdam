package io.github.gjaku1031.damdam.dashboard.dto;

import java.util.List;
import java.util.Map;

import io.github.gjaku1031.damdam.order.domain.OrderStatus;

/**
 * 운영 대시보드 요약
 *
 * 날짜 경계는 한국 시간(Asia/Seoul) 기준
 *
 * @param orderCounts 주문 상태별 건수, 모든 상태 키 포함(없으면 0), 상태 선언 순서
 * @param todayOrderCount 오늘 생성된 주문 수(상태 무관)
 * @param todaySalesAmount 오늘 결제 완료된 주문의 결제 금액에서 환불 금액을 뺀 합계(원)
 * @param monthSalesAmount 이번 달 결제 완료된 주문의 결제 금액에서 환불 금액을 뺀 합계(원)
 * @param lowStock 재고 3개 이하 판매 옵션, 재고 적은 순·상품명 순 최대 10개
 */
public record DashboardResponse(Map<OrderStatus, Long> orderCounts, long todayOrderCount, long todaySalesAmount,
        long monthSalesAmount, List<LowStockItem> lowStock) {}

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
 * @param todaySalesAmount 오늘 결제 완료된 주문의 결제 금액에서 전액 환불 금액을 뺀 합계(원), 결제 후 취소한 주문 제외. 일부 환불은 금액을 기록하지 않아 차감하지 않음
 * @param monthSalesAmount 이번 달 결제 완료된 주문의 결제 금액에서 전액 환불 금액을 뺀 합계(원), 기준은 오늘 매출과 같음
 * @param lowStock 재고 3개 이하 판매 옵션, 재고 적은 순·상품명 순 최대 10개
 * @param paymentDueTodayCount 입금 기한이 오늘 안에 끝나는 결제 대기 주문 수(이미 지났지만 아직 취소 전인 주문 포함)
 */
public record DashboardResponse(Map<OrderStatus, Long> orderCounts, long todayOrderCount, long todaySalesAmount,
        long monthSalesAmount, List<LowStockItem> lowStock, long paymentDueTodayCount) {}

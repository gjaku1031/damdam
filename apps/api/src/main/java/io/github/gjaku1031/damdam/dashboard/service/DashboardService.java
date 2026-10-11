package io.github.gjaku1031.damdam.dashboard.service;

import static io.github.gjaku1031.damdam.jooq.Tables.ORDERS;
import static io.github.gjaku1031.damdam.jooq.Tables.PRODUCTS;
import static io.github.gjaku1031.damdam.jooq.Tables.PRODUCT_VARIANTS;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.gjaku1031.damdam.dashboard.dto.DashboardResponse;
import io.github.gjaku1031.damdam.dashboard.dto.LowStockItem;
import io.github.gjaku1031.damdam.order.domain.OrderStatus;
import lombok.RequiredArgsConstructor;

/**
 * 운영 대시보드 집계
 */
@Service
@RequiredArgsConstructor
public class DashboardService {
    /**
     * 오늘·이번 달 경계를 정하는 시간대
     */
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /**
     * 재고 부족으로 보는 최대 재고
     */
    private static final int LOW_STOCK_THRESHOLD = 3;

    /**
     * 재고 부족 목록 최대 개수
     */
    private static final int LOW_STOCK_LIMIT = 10;

    /**
     * 집계용 jOOQ 문맥
     */
    private final DSLContext dsl;

    /**
     * 대시보드 요약 조회
     *
     * 1. 주문 상태별 건수(없는 상태는 0)
     * 2. 오늘 생성 주문 수, 오늘·이번 달 순매출(결제 시각 기준, 환불 차감·결제 후 취소 제외)
     * 3. 재고 부족 판매 옵션, 오늘 입금 기한이 끝나는 결제 대기 주문 수
     *
     * @return 대시보드 요약
     */
    @Transactional(readOnly = true)
    public DashboardResponse summary() {
        // 상태별 건수, 모든 상태를 0으로 채운 뒤 집계값 반영
        Map<OrderStatus, Long> counts = new LinkedHashMap<>();
        for (OrderStatus status : OrderStatus.values()) counts.put(status, 0L);
        dsl.select(ORDERS.STATUS, DSL.count()).from(ORDERS).groupBy(ORDERS.STATUS)
                .forEach(row -> counts.put(OrderStatus.valueOf(row.value1()), row.value2().longValue()));

        // 한국 시간 기준 오늘·이번 달 시작 시각
        LocalDate today = LocalDate.now(SEOUL);
        Instant todayStart = today.atStartOfDay(SEOUL).toInstant();
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(SEOUL).toInstant();
        long todayOrders = dsl.fetchCount(ORDERS, ORDERS.CREATEDAT.ge(todayStart));
        long dueToday = dsl.fetchCount(ORDERS, ORDERS.STATUS.eq(OrderStatus.PENDING_PAYMENT.name()),
                ORDERS.PAYMENTDUEAT.lt(today.plusDays(1).atStartOfDay(SEOUL).toInstant()));

        return new DashboardResponse(counts, todayOrders, salesSince(todayStart), salesSince(monthStart), lowStock(),
                dueToday);
    }

    /**
     * 기준 시각 이후 결제된 주문의 순매출
     *
     * 결제 후 취소한 주문은 받은 돈을 돌려주므로 매출에서 제외
     *
     * @param from 포함하는 시작 시각
     * @return 결제 금액에서 환불 금액을 뺀 합계(원), 없으면 0
     */
    private long salesSince(Instant from) {
        BigDecimal sum = dsl.select(DSL.sum(ORDERS.FINALPAYMENTAMOUNT.minus(ORDERS.REFUNDEDAMOUNT)))
                .from(ORDERS)
                .where(ORDERS.PAIDAT.ge(from), ORDERS.STATUS.ne(OrderStatus.CANCELED.name()))
                .fetchOne(0, BigDecimal.class);
        return sum == null ? 0 : sum.longValue();
    }

    /**
     * 재고 부족 판매 옵션 조회
     *
     * @return 삭제되지 않은 상품의 판매 중인 옵션 중 재고가 기준 이하인 것, 재고 적은 순·상품명 순
     */
    private List<LowStockItem> lowStock() {
        return dsl.select(PRODUCTS.ID, PRODUCTS.NAME, PRODUCT_VARIANTS.ID, PRODUCT_VARIANTS.NAME, PRODUCT_VARIANTS.STOCK)
                .from(PRODUCT_VARIANTS)
                .join(PRODUCTS).on(PRODUCTS.ID.eq(PRODUCT_VARIANTS.PRODUCTID))
                .where(PRODUCTS.DELETEDAT.isNull(), PRODUCT_VARIANTS.ACTIVE.isTrue(),
                        PRODUCT_VARIANTS.STOCK.le(LOW_STOCK_THRESHOLD))
                .orderBy(PRODUCT_VARIANTS.STOCK.asc(), PRODUCTS.NAME.asc(), PRODUCT_VARIANTS.NAME.asc())
                .limit(LOW_STOCK_LIMIT)
                .fetch(row -> new LowStockItem(row.value1(), row.value2(), row.value3(), row.value4(), row.value5()));
    }
}

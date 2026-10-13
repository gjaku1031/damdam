package io.github.gjaku1031.damdam.system.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.gjaku1031.damdam.inventory.dto.StockLedgerMismatch;
import io.github.gjaku1031.damdam.inventory.service.InventoryService;
import io.github.gjaku1031.damdam.order.service.OrderService;
import io.github.gjaku1031.damdam.storage.service.StorageService;
import lombok.RequiredArgsConstructor;

/**
 * 시간이 지나면 정리해야 하는 데이터(입금 기한이 지난 주문, 업로드 티켓)의 주기적 처리와 재고·이력 대조 점검
 *
 * 트랜잭션은 각 대상 서비스에서 처리
 */
@Component
@RequiredArgsConstructor
public final class SchedulerService {
    /**
     * 정리 결과 로거
     */
    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    /**
     * 한 번 실행에 처리할 기한 지난 주문 최대 건수, 남은 주문은 다음 실행에서 처리
     */
    private static final int OVERDUE_BATCH_SIZE = 500;

    /**
     * 주문 서비스
     */
    private final OrderService orders;

    /**
     * 사진 저장 서비스
     */
    private final StorageService storage;

    /**
     * 재고·이력 대조
     */
    private final InventoryService inventory;

    /**
     * 입금 기한이 지난 결제 대기 주문 자동 취소, 5분마다 실행
     *
     * 주문마다 별도 트랜잭션으로 처리해 한 주문의 실패가 다른 주문을 막지 않음
     * 여러 인스턴스가 같은 주문을 동시에 처리해도 주문 행 잠금과 재확인으로 한 번만 취소됨
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void cancelPaymentOverdueOrders() {
        List<UUID> ids = orders.paymentOverdueOrderIds(OVERDUE_BATCH_SIZE);
        int canceled = 0;
        int failed = 0;
        for (UUID id : ids) {
            try {
                if (orders.cancelIfPaymentOverdue(id)) canceled++;
            } catch (RuntimeException e) {
                failed++;
                log.error("입금 기한이 지난 주문 {} 자동 취소에 실패했습니다", id, e);
            }
        }
        if (canceled > 0 || failed > 0) {
            log.info("입금 기한이 지난 주문 {}건을 자동 취소했습니다 (실패 {}건)", canceled, failed);
        }
    }

    /**
     * 기한이 지난 업로드 티켓 실패 처리, 매시 정각 실행
     */
    @Scheduled(cron = "0 0 * * * *")
    public void failExpiredTickets() {
        int count = storage.failExpiredTickets();
        if (count > 0) log.info("{}건의 만료된 업로드 티켓을 정리했습니다", count);
    }

    /**
     * 현재 재고와 재고 이력 합계 대조, 매시 30분 실행
     *
     * 어긋난 옵션은 고치지 않고 오류 로그로만 알림, 원인(이력 없이 재고를 바꾼 경로)을 찾는 것이 먼저이기 때문임
     * 같은 내용을 운영자 대시보드에도 표시하므로, 이 로그는 화면을 열지 않아도 남는 기록 역할
     */
    @Scheduled(cron = "0 30 * * * *")
    public void checkStockLedger() {
        for (StockLedgerMismatch mismatch : inventory.ledgerMismatches()) {
            log.error("재고와 재고 이력이 맞지 않습니다: {} · {} (옵션 {}) 현재 재고 {}, 이력 합계 {}", mismatch.productName(),
                    mismatch.variantName(), mismatch.variantId(), mismatch.stock(), mismatch.ledgerTotal());
        }
    }
}

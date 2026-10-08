package io.github.gjaku1031.damdam.system.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.gjaku1031.damdam.inventory.service.InventoryService;
import io.github.gjaku1031.damdam.storage.service.StorageService;
import lombok.RequiredArgsConstructor;

/**
 * 시간이 지나면 정리해야 하는 데이터(재고 예약, 업로드 티켓)의 주기적 만료 처리
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
     * 재고 예약 서비스
     */
    private final InventoryService inventory;

    /**
     * 사진 저장 서비스
     */
    private final StorageService storage;

    /**
     * 기한이 지난 재고 예약 만료 처리, 5분마다 실행
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void expireReservations() {
        int count = inventory.expireOverdue();
        if (count > 0) log.info("{}건의 만료된 재고 예약을 정리했습니다", count);
    }

    /**
     * 기한이 지난 업로드 티켓 실패 처리, 매시 정각 실행
     */
    @Scheduled(cron = "0 0 * * * *")
    public void failExpiredTickets() {
        int count = storage.failExpiredTickets();
        if (count > 0) log.info("{}건의 만료된 업로드 티켓을 정리했습니다", count);
    }
}

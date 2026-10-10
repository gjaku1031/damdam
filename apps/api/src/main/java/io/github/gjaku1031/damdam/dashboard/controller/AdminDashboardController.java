package io.github.gjaku1031.damdam.dashboard.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.gjaku1031.damdam.common.auth.AdminOnly;
import io.github.gjaku1031.damdam.dashboard.dto.DashboardResponse;
import io.github.gjaku1031.damdam.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;

/**
 * 운영 대시보드 API
 *
 * {@code /api/admin/**} 경로라 운영자 권한 검사가 항상 적용됨, 조회라 운영 기록을 남기지 않음
 */
@RestController
@AdminOnly
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public final class AdminDashboardController {
    /**
     * 대시보드 집계 서비스
     */
    private final DashboardService dashboard;

    /**
     * 대시보드 요약 조회
     *
     * @return 주문 상태별 건수·오늘 주문·매출·재고 부족 옵션
     */
    @GetMapping
    public DashboardResponse summary() {
        return dashboard.summary();
    }
}

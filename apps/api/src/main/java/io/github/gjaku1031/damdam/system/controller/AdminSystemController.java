package io.github.gjaku1031.damdam.system.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.gjaku1031.damdam.common.auth.AdminOnly;
import io.github.gjaku1031.damdam.common.auth.CurrentUser;
import io.github.gjaku1031.damdam.common.web.ItemPage;
import io.github.gjaku1031.damdam.common.web.Paging;
import io.github.gjaku1031.damdam.system.dto.AuditLogResponse;
import io.github.gjaku1031.damdam.system.dto.SetConfigRequest;
import io.github.gjaku1031.damdam.system.dto.SystemConfigResponse;
import io.github.gjaku1031.damdam.system.service.SystemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 운영자용 운영 기록·설정 API
 *
 * {@code /api/admin/**} 경로라 운영자 권한 검사가 항상 적용됨
 */
@RestController
@AdminOnly
@RequestMapping("/admin/system")
@RequiredArgsConstructor
public final class AdminSystemController {
    /**
     * 운영 기록·설정 서비스
     */
    private final SystemService system;

    /**
     * 운영 기록 목록 조회
     *
     * @param page 쪽 번호, 생략 시 1
     * @param size 쪽 크기, 생략 시 20
     * @param target 대상 종류({@code ORDERS} 등), 대소문자 무시, 생략 시 전체
     * @return 최신순 기록 목록
     */
    @GetMapping("/audit-logs")
    public ItemPage<AuditLogResponse> auditLogs(@RequestParam(required = false) String page,
            @RequestParam(required = false) String size, @RequestParam(defaultValue = "") String target) {
        return system.auditLogs(Paging.of(page, size, 20), target);
    }

    /**
     * 전체 설정 조회
     *
     * @return 설정 키 오름차순 목록
     */
    @GetMapping("/configs")
    public List<SystemConfigResponse> configs() {
        return system.allConfigs();
    }

    /**
     * 설정 추가·수정
     *
     * @param body 설정 저장 요청
     * @param user 현재 운영자
     * @return 저장 후 설정
     */
    @PostMapping("/configs")
    @ResponseStatus(HttpStatus.CREATED)
    public SystemConfigResponse setConfig(@Valid @RequestBody SetConfigRequest body, CurrentUser user) {
        return system.setConfig(body.key(), body.value(), body.description(), user.id());
    }
}

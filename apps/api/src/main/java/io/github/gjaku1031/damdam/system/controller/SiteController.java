package io.github.gjaku1031.damdam.system.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.gjaku1031.damdam.system.dto.SiteInfoResponse;
import io.github.gjaku1031.damdam.system.service.SystemService;
import lombok.RequiredArgsConstructor;

/**
 * 공개 사업자·고객센터 안내 API
 *
 * 화면 하단 사업자 정보와 주문 화면의 입금 안내에 사용
 */
@RestController
@RequestMapping("/site")
@RequiredArgsConstructor
public final class SiteController {
    /**
     * 운영 설정 조회 서비스
     */
    private final SystemService system;

    /**
     * 공개 안내 조회
     *
     * @return 사업자 정보·고객센터·입금 안내, 설정하지 않은 값은 빈 문자열
     */
    @GetMapping
    public SiteInfoResponse site() {
        return system.siteInfo();
    }
}

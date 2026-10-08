package io.github.gjaku1031.damdam.payment.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.gjaku1031.damdam.system.domain.SystemConfigEntity;
import io.github.gjaku1031.damdam.system.service.SystemService;
import lombok.RequiredArgsConstructor;

/**
 * 공개 결제 안내 API
 *
 * 결제 수단은 무통장 입금 하나, 운영자가 입금 확인 후 주문 상태 변경
 */
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public final class PaymentController {
    /**
     * 운영 설정 조회
     */
    private final SystemService system;

    /**
     * 무통장 입금 안내 조회
     *
     * @return {@code {instructions}}, 설정이 없으면 빈 문자열
     */
    @GetMapping("/bank-instructions")
    public Map<String, String> bankInstructions() {
        return Map.of("instructions", system.config(SystemConfigEntity.BANK_TRANSFER_ACCOUNT).orElse(""));
    }
}

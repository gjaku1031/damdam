package io.github.gjaku1031.damdam.system.dto;

import java.util.Map;

import io.github.gjaku1031.damdam.system.domain.PaymentDeadlinePolicy;
import io.github.gjaku1031.damdam.system.domain.SystemConfigEntity;

/**
 * 공개 사업자·고객센터·입금 안내
 *
 * 설정하지 않은 값은 빈 문자열
 *
 * @param businessName 상호
 * @param ownerName 대표자
 * @param businessNumber 사업자등록번호
 * @param mailOrderNumber 통신판매업 신고번호
 * @param address 사업장 주소
 * @param csPhone 고객센터 전화번호
 * @param csHours 고객센터 운영 시간
 * @param csEmail 고객센터 이메일
 * @param bankInstructions 무통장 입금 계좌 안내
 * @param paymentDueDays 무통장 입금 기한 일수, 주문일(한국 시간)에서 이 일수 뒤의 23:59까지 입금 대기
 */
public record SiteInfoResponse(String businessName, String ownerName, String businessNumber, String mailOrderNumber,
        String address, String csPhone, String csHours, String csEmail, String bankInstructions, int paymentDueDays) {
    /**
     * 설정 키별 값에서 생성
     *
     * @param values 설정 키별 값, 없는 키는 빈 문자열(입금 기한은 기본 일수)로 처리
     * @return 공개 안내
     */
    public static SiteInfoResponse of(Map<String, String> values) {
        return new SiteInfoResponse(value(values, SystemConfigEntity.BUSINESS_NAME),
                value(values, SystemConfigEntity.BUSINESS_OWNER), value(values, SystemConfigEntity.BUSINESS_NUMBER),
                value(values, SystemConfigEntity.MAIL_ORDER_NUMBER), value(values, SystemConfigEntity.BUSINESS_ADDRESS),
                value(values, SystemConfigEntity.CS_PHONE), value(values, SystemConfigEntity.CS_HOURS),
                value(values, SystemConfigEntity.CS_EMAIL), value(values, SystemConfigEntity.BANK_TRANSFER_ACCOUNT),
                PaymentDeadlinePolicy.daysOrDefault(values.get(SystemConfigEntity.BANK_TRANSFER_DUE_DAYS)));
    }

    /**
     * 설정 값 조회
     *
     * @param values 설정 키별 값
     * @param key 설정 키
     * @return 설정 값, 없으면 빈 문자열
     */
    private static String value(Map<String, String> values, String key) {
        return values.getOrDefault(key, "");
    }
}

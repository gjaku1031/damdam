package io.github.gjaku1031.damdam.system.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 운영 설정 키-값
 */
@Getter
@Entity
@Table(name = "system_configs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemConfigEntity {
    /**
     * 무통장 입금 안내 설정 키
     */
    public static final String BANK_TRANSFER_ACCOUNT = "bank_transfer_account";

    /**
     * 무통장 입금 기한 일수 설정 키, 값 규칙은 {@link PaymentDeadlinePolicy}
     */
    public static final String BANK_TRANSFER_DUE_DAYS = "bank_transfer_due_days";

    /**
     * 상호 설정 키
     */
    public static final String BUSINESS_NAME = "business_name";

    /**
     * 대표자 설정 키
     */
    public static final String BUSINESS_OWNER = "business_owner";

    /**
     * 사업자등록번호 설정 키
     */
    public static final String BUSINESS_NUMBER = "business_number";

    /**
     * 통신판매업 신고번호 설정 키
     */
    public static final String MAIL_ORDER_NUMBER = "mail_order_number";

    /**
     * 사업장 주소 설정 키
     */
    public static final String BUSINESS_ADDRESS = "business_address";

    /**
     * 고객센터 전화번호 설정 키
     */
    public static final String CS_PHONE = "cs_phone";

    /**
     * 고객센터 운영 시간 설정 키
     */
    public static final String CS_HOURS = "cs_hours";

    /**
     * 고객센터 이메일 설정 키
     */
    public static final String CS_EMAIL = "cs_email";

    /**
     * 설정 키
     */
    @Id
    @Column(length = 80)
    private String configKey;

    /**
     * 설정 값
     */
    @Column(nullable = false, columnDefinition = "text")
    private String configValue;

    /**
     * 설명
     */
    private String description;

    /**
     * 마지막으로 바꾼 운영자 ID, 기본 데이터면 {@code null}
     */
    @Column(name = "updated_by_user_id")
    private UUID updatedByUserId;

    /**
     * 수정 시각
     */
    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * 설정 생성
     *
     * @return 저장 전 설정
     */
    public static SystemConfigEntity of(String key, String value, String description, UUID updatedByUserId) {
        SystemConfigEntity config = new SystemConfigEntity();
        config.configKey = key;
        config.configValue = value;
        config.description = description;
        config.updatedByUserId = updatedByUserId;
        return config;
    }

    /**
     * 값 변경
     *
     * @param description 새 설명, {@code null}이면 기존 설명 유지
     */
    public void update(String value, String description, UUID updatedByUserId) {
        this.configValue = value;
        if (description != null) this.description = description;
        this.updatedByUserId = updatedByUserId;
    }

    /**
     * 저장·변경 시 수정 시각 갱신
     */
    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = Instant.now();
    }
}

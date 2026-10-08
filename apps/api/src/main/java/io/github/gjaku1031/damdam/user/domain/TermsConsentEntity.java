package io.github.gjaku1031.damdam.user.domain;

import java.util.UUID;

import io.github.gjaku1031.damdam.common.jpa.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원의 약관 동의 기록
 */
@Getter
@Entity
@Table(name = "terms_consents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsConsentEntity extends BaseEntity {
    /**
     * 회원 ID
     */
    private UUID userId;

    /**
     * 약관 ID
     */
    private UUID termsId;

    /**
     * 동의 기록 생성
     *
     * @return 저장 전 동의 기록
     */
    public static TermsConsentEntity of(UUID userId, UUID termsId) {
        TermsConsentEntity consent = new TermsConsentEntity();
        consent.userId = userId;
        consent.termsId = termsId;
        return consent;
    }
}

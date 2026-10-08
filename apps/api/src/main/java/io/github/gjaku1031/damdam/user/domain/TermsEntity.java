package io.github.gjaku1031.damdam.user.domain;

import java.time.Instant;

import io.github.gjaku1031.damdam.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 약관 버전
 */
@Getter
@Entity
@Table(name = "terms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsEntity extends BaseEntity {
    /**
     * 현재 필수 약관 버전, 회원가입·기본 데이터에서 공유
     */
    public static final String CURRENT_VERSION = "2026-10";

    /**
     * 현재 필수 약관 시행 시각(2026-10-03 00:00 KST)
     */
    public static final Instant CURRENT_EFFECTIVE_FROM = Instant.parse("2026-10-02T15:00:00Z");

    /**
     * 약관 종류
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TermsType type;

    /**
     * 버전
     */
    @Column(nullable = false, length = 40)
    private String version;

    /**
     * 제목
     */
    @Column(nullable = false)
    private String title;

    /**
     * 필수 동의 여부
     */
    @Column(nullable = false)
    private boolean isRequired;

    /**
     * 시행 시각
     */
    @Column(nullable = false)
    private Instant effectiveFrom;

    /**
     * 현재 버전의 필수 약관 생성
     *
     * @param type {@link TermsType#SERVICE} 또는 {@link TermsType#PRIVACY}
     * @return 저장 전 약관
     */
    public static TermsEntity current(TermsType type) {
        TermsEntity terms = new TermsEntity();
        terms.type = type;
        terms.version = CURRENT_VERSION;
        terms.title = type == TermsType.SERVICE ? "이용약관" : "개인정보처리방침";
        terms.isRequired = true;
        terms.effectiveFrom = CURRENT_EFFECTIVE_FROM;
        return terms;
    }
}

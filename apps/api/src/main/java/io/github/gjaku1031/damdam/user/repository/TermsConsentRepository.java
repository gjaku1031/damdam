package io.github.gjaku1031.damdam.user.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.gjaku1031.damdam.user.domain.TermsConsentEntity;

/**
 * 약관 동의 기록 저장소
 */
public interface TermsConsentRepository extends JpaRepository<TermsConsentEntity, UUID> {
    /**
     * 회원의 약관 동의 건수
     *
     * @param userId 회원 ID
     * @return 동의 기록 수
     */
    long countByUserId(UUID userId);
}

package io.github.gjaku1031.damdam.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.gjaku1031.damdam.user.domain.TermsEntity;
import io.github.gjaku1031.damdam.user.domain.TermsType;

/**
 * 약관 저장소
 */
public interface TermsRepository extends JpaRepository<TermsEntity, UUID> {
    /**
     * 종류·버전으로 약관 조회
     *
     * @param type 약관 종류
     * @param version 약관 버전
     * @return 약관, 없으면 빈 값
     */
    Optional<TermsEntity> findByTypeAndVersion(TermsType type, String version);
}

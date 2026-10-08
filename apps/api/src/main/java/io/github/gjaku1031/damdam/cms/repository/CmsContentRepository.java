package io.github.gjaku1031.damdam.cms.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.gjaku1031.damdam.cms.domain.CmsContentEntity;

/**
 * 공지·FAQ 저장소
 */
public interface CmsContentRepository extends JpaRepository<CmsContentEntity, UUID> {
    /**
     * 삭제되지 않은 글 조회
     *
     * @return 글, 없거나 삭제됐으면 빈 값
     */
    Optional<CmsContentEntity> findByIdAndDeletedAtIsNull(UUID id);
}

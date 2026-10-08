package io.github.gjaku1031.damdam.cms.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.gjaku1031.damdam.cms.domain.CmsContentEntity;
import io.github.gjaku1031.damdam.cms.domain.CmsContentType;

/**
 * 공지·FAQ 응답
 *
 * @param id ID
 * @param createdAt 작성 시각
 * @param updatedAt 수정 시각
 * @param deletedAt 삭제 시각, 삭제 응답에서만 값 있음
 * @param type 공지·FAQ 구분
 * @param title 제목
 * @param bodyHtml 정제된 본문 HTML
 * @param visible 공개 여부
 * @param authorUserId 작성 운영자 ID
 */
public record CmsContentResponse(UUID id, Instant createdAt, Instant updatedAt, Instant deletedAt, CmsContentType type,
        String title, String bodyHtml, boolean visible, UUID authorUserId) {
    /**
     * 엔티티 변환
     *
     * @return 응답
     */
    public static CmsContentResponse from(CmsContentEntity content) {
        return new CmsContentResponse(content.getId(), content.getCreatedAt(), content.getUpdatedAt(),
                content.getDeletedAt(), content.getType(), content.getTitle(), content.getBodyHtml(),
                content.isVisible(), content.getAuthorUserId());
    }
}

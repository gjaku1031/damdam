package io.github.gjaku1031.damdam.feed.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.gjaku1031.damdam.feed.domain.PostCategoryEntity;

/**
 * 게시글 분류
 *
 * @param id ID
 * @param createdAt 생성 시각
 * @param updatedAt 수정 시각
 * @param name 분류명
 */
public record CategoryResponse(UUID id, Instant createdAt, Instant updatedAt, String name) {
    /**
     * 엔티티 변환
     *
     * @return 응답
     */
    public static CategoryResponse from(PostCategoryEntity category) {
        return new CategoryResponse(category.getId(), category.getCreatedAt(), category.getUpdatedAt(),
                category.getName());
    }
}

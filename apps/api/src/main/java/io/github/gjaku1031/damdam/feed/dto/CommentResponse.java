package io.github.gjaku1031.damdam.feed.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 댓글 응답
 *
 * @param id ID
 * @param postId 게시글 ID
 * @param userId 작성자 ID
 * @param content 내용
 * @param createdAt 작성 시각
 * @param hiddenAt 운영자 숨김 시각
 * @param user 작성자 아이디, 회원이 없으면 {@code null}
 */
public record CommentResponse(UUID id, UUID postId, UUID userId, String content, Instant createdAt, Instant hiddenAt,
        Author user) {
    /**
     * 댓글 작성자
     *
     * @param id 회원 ID
     * @param username 아이디
     */
    public record Author(UUID id, String username) {}
}

package io.github.gjaku1031.damdam.feed.domain;

import java.time.Instant;
import java.util.UUID;

import io.github.gjaku1031.damdam.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시글 댓글
 *
 * 작성자 삭제(deletedAt)와 운영자 숨김(hiddenAt)을 구분해 기록
 */
@Getter
@Entity
@Table(name = "post_comments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedPostCommentEntity extends BaseEntity {
    /**
     * 게시글 ID
     */
    @Column(name = "post_id", nullable = false)
    private UUID postId;

    /**
     * 작성자 ID
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * 내용
     */
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /**
     * 운영자 숨김 시각
     */
    private Instant hiddenAt;

    /**
     * 숨긴 운영자 ID
     */
    @Column(name = "hidden_by_user_id")
    private UUID hiddenByUserId;

    /**
     * 작성자 삭제 시각
     */
    private Instant deletedAt;

    /**
     * 삭제한 사용자 ID
     */
    @Column(name = "deleted_by_user_id")
    private UUID deletedByUserId;

    /**
     * 댓글 작성
     *
     * @param content 앞뒤 공백을 제거한 내용
     * @return 저장 전 댓글
     */
    public static FeedPostCommentEntity write(UUID postId, UUID userId, String content) {
        FeedPostCommentEntity comment = new FeedPostCommentEntity();
        comment.postId = postId;
        comment.userId = userId;
        comment.content = content;
        return comment;
    }

    /**
     * 작성자 여부
     *
     * @return 작성자 ID가 같으면 {@code true}
     */
    public boolean isOwnedBy(UUID userId) {
        return this.userId.equals(userId);
    }

    /**
     * 운영자 숨김 여부
     *
     * @return 숨김 시각이 있으면 {@code true}
     */
    public boolean isHidden() {
        return hiddenAt != null;
    }

    /**
     * 작성자 삭제
     */
    public void deleteBy(UUID userId) {
        deletedAt = Instant.now();
        deletedByUserId = userId;
    }

    /**
     * 운영자 숨김
     */
    public void hideBy(UUID adminUserId) {
        hiddenAt = Instant.now();
        hiddenByUserId = adminUserId;
    }
}

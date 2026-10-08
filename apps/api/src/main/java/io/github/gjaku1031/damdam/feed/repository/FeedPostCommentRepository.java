package io.github.gjaku1031.damdam.feed.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import io.github.gjaku1031.damdam.feed.domain.FeedPostCommentEntity;
import jakarta.persistence.LockModeType;

/**
 * 댓글 저장소
 */
public interface FeedPostCommentRepository extends JpaRepository<FeedPostCommentEntity, UUID> {
    /**
     * 삭제되지 않은 게시글 댓글 잠금 조회
     *
     * @return 같은 게시글의 삭제 전 댓글
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from FeedPostCommentEntity c where c.id = :id and c.postId = :postId and c.deletedAt is null")
    Optional<FeedPostCommentEntity> findActiveForUpdate(UUID id, UUID postId);
}

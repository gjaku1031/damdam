package io.github.gjaku1031.damdam.feed.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import io.github.gjaku1031.damdam.feed.domain.FeedPostImageEntity;

/**
 * 발행 게시글 사진 목록 저장소
 */
public interface FeedPostImageRepository extends JpaRepository<FeedPostImageEntity, UUID> {
    /**
     * 게시글 사진 목록 즉시 삭제
     *
     * 등장 순서 유일 제약 때문에 새 목록을 저장하기 전에 실행
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from FeedPostImageEntity i where i.postId = :postId")
    void deleteAllOfPost(UUID postId);
}

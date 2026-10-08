package io.github.gjaku1031.damdam.feed.domain;

/**
 * 게시글 상태
 */
public enum PostState {
    /**
     * 발행 전 작성 중
     */
    DRAFT,

    /**
     * 공개 발행
     */
    PUBLISHED,

    /**
     * 발행 후 비공개
     */
    PRIVATE
}

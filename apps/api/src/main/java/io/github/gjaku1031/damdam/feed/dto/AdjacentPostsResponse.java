package io.github.gjaku1031.damdam.feed.dto;

/**
 * 공개 게시글의 이전·다음 글
 *
 * 발행 시각 순서 기준, 같은 시각이면 ID 순
 *
 * @param previous 바로 이전(더 오래된) 공개 글, 없으면 {@code null}
 * @param next 바로 다음(더 최근) 공개 글, 없으면 {@code null}
 */
public record AdjacentPostsResponse(PostLink previous, PostLink next) {}

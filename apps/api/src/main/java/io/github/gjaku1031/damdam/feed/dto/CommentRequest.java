package io.github.gjaku1031.damdam.feed.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 댓글 작성 요청
 *
 * @param content 내용, 최대 2000자, 앞뒤 공백 제거 후 비어 있으면 400
 */
public record CommentRequest(@NotNull @Size(max = 2000) String content) {}

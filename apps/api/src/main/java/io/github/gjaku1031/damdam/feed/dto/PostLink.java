package io.github.gjaku1031.damdam.feed.dto;

import java.util.UUID;

/**
 * 다른 게시글로 가는 링크
 *
 * @param id 게시글 ID
 * @param title 발행 제목
 */
public record PostLink(UUID id, String title) {}

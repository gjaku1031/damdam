package io.github.gjaku1031.damdam.feed.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 발행·비공개 요청
 *
 * @param revision 화면이 알고 있는 현재 리비전
 */
public record RevisionRequest(@NotNull @Min(0) Integer revision) {}

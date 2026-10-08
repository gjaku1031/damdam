package io.github.gjaku1031.damdam.cms.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * 공지·FAQ 수정 요청, 세 값을 모두 받아 일괄 변경
 *
 * @param title 제목
 * @param bodyHtml 본문 HTML, 저장 전 정제
 * @param visible 공개 여부
 */
public record UpdateCmsContentRequest(@NotEmpty String title, @NotEmpty String bodyHtml, @NotNull Boolean visible) {}

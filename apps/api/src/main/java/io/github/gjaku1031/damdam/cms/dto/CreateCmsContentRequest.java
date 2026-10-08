package io.github.gjaku1031.damdam.cms.dto;

import jakarta.validation.constraints.NotEmpty;

/**
 * 공지·FAQ 등록 요청
 *
 * @param title 제목
 * @param bodyHtml 본문 HTML, 저장 전 정제
 */
public record CreateCmsContentRequest(@NotEmpty String title, @NotEmpty String bodyHtml) {}

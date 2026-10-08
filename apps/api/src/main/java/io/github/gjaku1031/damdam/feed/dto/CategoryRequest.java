package io.github.gjaku1031.damdam.feed.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 분류 등록·이름 변경 요청
 *
 * @param name 분류명, 최대 40자, 앞뒤 공백 제거 후 비어 있으면 400
 */
public record CategoryRequest(@NotNull @Size(max = 40) String name) {}

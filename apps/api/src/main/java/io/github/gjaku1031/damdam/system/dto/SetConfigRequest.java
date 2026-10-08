package io.github.gjaku1031.damdam.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 운영 설정 저장 요청
 *
 * @param key 설정 키, 공백 불가·최대 100자
 * @param value 설정 값, 빈 문자열 허용·최대 2000자
 * @param description 설정 설명, {@code null}이면 기존 설명 유지
 */
public record SetConfigRequest(
        @NotBlank @Size(max = 100) String key,
        @NotNull @Size(max = 2000) String value,
        String description) {}

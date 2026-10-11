package io.github.gjaku1031.damdam.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 비밀번호 변경 요청
 *
 * @param currentPassword 현재 비밀번호, 1~128자
 * @param newPassword 새 비밀번호, 가입과 같은 {@link AuthRules#PASSWORD} 형식 검사
 */
public record ChangePasswordRequest(
        @NotNull @Size(min = 1, max = 128) String currentPassword,
        @NotNull @Pattern(regexp = AuthRules.PASSWORD, message = AuthRules.PASSWORD_MESSAGE) String newPassword) {}

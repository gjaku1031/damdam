package io.github.gjaku1031.damdam.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 비밀번호 변경 요청
 *
 * @param currentPassword 현재 비밀번호, 1~128자
 * @param newPassword 새 비밀번호, 가입과 같은 12~128자
 */
public record ChangePasswordRequest(
        @NotNull @Size(min = 1, max = 128) String currentPassword,
        @NotNull @Size(min = 12, max = 128, message = "비밀번호는 12~128자로 입력해주세요.") String newPassword) {}

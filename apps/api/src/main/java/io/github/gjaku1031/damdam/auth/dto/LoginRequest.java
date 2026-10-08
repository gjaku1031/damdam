package io.github.gjaku1031.damdam.auth.dto;

import java.util.Locale;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 로그인 요청
 *
 * @param username 아이디, 앞뒤 공백 제거·소문자 변환 후 {@link AuthRules#USERNAME} 형식 검사
 * @param password 비밀번호, 1~128자
 */
public record LoginRequest(
        @NotNull @Pattern(regexp = AuthRules.USERNAME, message = AuthRules.USERNAME_MESSAGE) String username,
        @NotNull @Size(min = 1, max = 128) String password) {
    /**
     * 아이디 정규화, 앞뒤 공백을 지우고 소문자로 비교
     */
    public LoginRequest {
        if (username != null) username = username.trim().toLowerCase(Locale.ROOT);
    }
}

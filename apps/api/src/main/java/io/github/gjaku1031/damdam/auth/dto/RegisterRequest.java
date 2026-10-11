package io.github.gjaku1031.damdam.auth.dto;

import java.util.Locale;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 고객 가입 요청
 *
 * @param username 아이디, 앞뒤 공백 제거·소문자 변환 후 {@link AuthRules#USERNAME} 형식 검사
 * @param password 비밀번호, {@link AuthRules#PASSWORD} 형식 검사
 * @param serviceTermsAccepted 이용약관 동의, {@code true}만 허용
 * @param privacyTermsAccepted 개인정보처리방침 동의, {@code true}만 허용
 */
public record RegisterRequest(
        @NotNull @Pattern(regexp = AuthRules.USERNAME, message = AuthRules.USERNAME_MESSAGE) String username,
        @NotNull @Pattern(regexp = AuthRules.PASSWORD, message = AuthRules.PASSWORD_MESSAGE) String password,
        @NotNull(message = "이용약관에 동의해주세요.") @AssertTrue(message = "이용약관에 동의해주세요.")
        Boolean serviceTermsAccepted,
        @NotNull(message = "개인정보처리방침에 동의해주세요.") @AssertTrue(message = "개인정보처리방침에 동의해주세요.")
        Boolean privacyTermsAccepted) {
    /**
     * 아이디 정규화, 앞뒤 공백을 지우고 소문자로 저장
     */
    public RegisterRequest {
        if (username != null) username = username.trim().toLowerCase(Locale.ROOT);
    }
}

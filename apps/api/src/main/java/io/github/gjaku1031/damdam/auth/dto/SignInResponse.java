package io.github.gjaku1031.damdam.auth.dto;

/**
 * 가입·로그인 성공 응답
 *
 * @param success 항상 {@code true}
 * @param user 로그인한 사용자 프로필
 */
public record SignInResponse(boolean success, AuthUserResponse user) {
    /**
     * 성공 응답 생성
     *
     * @param user 로그인한 사용자 프로필
     * @return {@code success=true} 응답
     */
    public static SignInResponse of(AuthUserResponse user) {
        return new SignInResponse(true, user);
    }
}

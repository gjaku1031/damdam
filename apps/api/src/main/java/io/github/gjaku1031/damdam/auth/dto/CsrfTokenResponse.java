package io.github.gjaku1031.damdam.auth.dto;

/**
 * CSRF 토큰 응답
 *
 * @param token 상태를 바꾸는 요청의 {@code X-CSRF-Token} 헤더에 실을 값
 */
public record CsrfTokenResponse(String token) {}

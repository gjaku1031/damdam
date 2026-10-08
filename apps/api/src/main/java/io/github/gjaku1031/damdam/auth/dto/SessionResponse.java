package io.github.gjaku1031.damdam.auth.dto;

/**
 * 현재 세션 사용자 응답
 *
 * 로그인하지 않았어도 200으로 응답하기 위한 래퍼
 *
 * @param user 로그인 사용자 프로필, 로그인하지 않았거나 계정이 삭제됐으면 {@code null}
 */
public record SessionResponse(AuthUserResponse user) {}

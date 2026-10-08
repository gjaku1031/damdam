package io.github.gjaku1031.damdam.auth.dto;

import java.util.UUID;

import io.github.gjaku1031.damdam.user.domain.UserEntity;
import io.github.gjaku1031.damdam.user.domain.UserRole;

/**
 * 로그인 사용자 프로필
 *
 * @param id 사용자 ID
 * @param username 로그인 아이디
 * @param role 권한
 */
public record AuthUserResponse(UUID id, String username, UserRole role) {
    /**
     * 계정 엔티티에서 생성
     *
     * @param user 계정
     * @return 비밀번호 해시를 제외한 프로필
     */
    public static AuthUserResponse from(UserEntity user) {
        return new AuthUserResponse(user.getId(), user.getUsername(), user.getRole());
    }
}

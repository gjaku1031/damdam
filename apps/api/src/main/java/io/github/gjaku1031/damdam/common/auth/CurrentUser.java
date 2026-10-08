package io.github.gjaku1031.damdam.common.auth;

import java.time.Instant;
import java.util.UUID;

import io.github.gjaku1031.damdam.user.domain.UserEntity;
import io.github.gjaku1031.damdam.user.domain.UserRole;

/**
 * 요청마다 DB에서 다시 읽은 로그인 사용자
 *
 * 권한·댓글 제한은 항상 현재 값으로 판단
 *
 * @param id 사용자 ID
 * @param username 로그인 아이디
 * @param role 권한
 * @param communityBlockedAt 댓글 작성 제한 시각, 제한이 없으면 {@code null}
 */
public record CurrentUser(UUID id, String username, UserRole role, Instant communityBlockedAt) {
    /**
     * 인증된 사용자를 담는 요청 속성 이름
     */
    static final String REQUEST_ATTRIBUTE = CurrentUser.class.getName();

    /**
     * 계정 엔티티에서 생성
     *
     * @param user 삭제되지 않은 계정
     * @return 현재 사용자
     */
    static CurrentUser from(UserEntity user) {
        return new CurrentUser(user.getId(), user.getUsername(), user.getRole(), user.getCommunityBlockedAt());
    }

    /**
     * 운영자 여부
     *
     * @return 운영자면 {@code true}
     */
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    /**
     * 댓글 작성 제한 여부
     *
     * @return 제한 시각이 있으면 {@code true}
     */
    public boolean isCommunityBlocked() {
        return communityBlockedAt != null;
    }
}

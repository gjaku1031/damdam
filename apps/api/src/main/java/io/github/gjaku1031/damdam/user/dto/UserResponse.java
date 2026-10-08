package io.github.gjaku1031.damdam.user.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.gjaku1031.damdam.user.domain.UserEntity;
import io.github.gjaku1031.damdam.user.domain.UserRole;

/**
 * 운영 화면용 회원 정보
 *
 * 비밀번호 해시는 포함하지 않음
 *
 * @param id 사용자 ID
 * @param createdAt 가입 시각
 * @param updatedAt 수정 시각
 * @param deletedAt 삭제 시각, 삭제 전이면 {@code null}
 * @param role 권한
 * @param username 로그인 아이디
 * @param communityBlockedAt 댓글 작성 제한 시각, 제한이 없으면 {@code null}
 */
public record UserResponse(UUID id, Instant createdAt, Instant updatedAt, Instant deletedAt, UserRole role,
        String username, Instant communityBlockedAt) {
    /**
     * 계정 엔티티에서 생성
     *
     * @param user 계정
     * @return 운영 화면용 회원 정보
     */
    public static UserResponse from(UserEntity user) {
        return new UserResponse(user.getId(), user.getCreatedAt(), user.getUpdatedAt(), user.getDeletedAt(),
                user.getRole(), user.getUsername(), user.getCommunityBlockedAt());
    }
}

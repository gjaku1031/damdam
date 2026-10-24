package io.github.gjaku1031.damdam.user.domain;

import java.time.Instant;
import java.util.UUID;

import io.github.gjaku1031.damdam.common.jpa.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 아이디·비밀번호 계정
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity extends SoftDeletableEntity {
    /**
     * 권한
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.CUSTOMER;

    /**
     * 로그인 아이디, 소문자로 정규화된 값
     */
    @Column(nullable = false, unique = true, length = 20)
    private String username;

    /**
     * Argon2id PHC 형식 비밀번호 해시
     */
    @Column(nullable = false)
    private String passwordHash;

    /**
     * 댓글 작성 제한 시각, 제한이 없으면 {@code null}
     */
    private Instant communityBlockedAt;

    /**
     * 댓글 작성을 제한한 운영자 ID
     */
    private UUID communityBlockedByUserId;

    /**
     * 새 계정 생성
     *
     * @param username 정규화된 아이디
     * @param passwordHash Argon2id 해시
     * @param role 권한
     * @return 저장 전 계정
     */
    public static UserEntity create(String username, String passwordHash, UserRole role) {
        UserEntity user = new UserEntity();
        user.username = username;
        user.passwordHash = passwordHash;
        user.role = role;
        return user;
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
     * 아이디·비밀번호 해시 교체
     *
     * 초기 운영자 계정을 현재 해시 형식으로 갱신할 때 사용
     */
    public void changeCredentials(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    /**
     * 비밀번호 해시 교체
     *
     * @param passwordHash 새 Argon2id 해시
     */
    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * 회원 탈퇴, 삭제 시각만 기록하고 주문 기록은 유지
     *
     * 아이디는 계속 점유되어 같은 아이디로 다시 가입할 수 없음
     */
    public void withdraw() {
        softDelete();
    }

    /**
     * 댓글 작성 제한, 이미 제한된 계정은 최초 제한 기록 유지
     *
     * @param blockedByUserId 제한한 운영자 ID
     */
    public void blockCommunity(UUID blockedByUserId) {
        if (communityBlockedAt == null) {
            communityBlockedAt = Instant.now();
            communityBlockedByUserId = blockedByUserId;
        }
    }

    /**
     * 댓글 작성 제한 해제
     */
    public void unblockCommunity() {
        communityBlockedAt = null;
        communityBlockedByUserId = null;
    }
}

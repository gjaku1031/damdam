package io.github.gjaku1031.damdam.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import io.github.gjaku1031.damdam.user.domain.UserEntity;
import io.github.gjaku1031.damdam.user.domain.UserRole;
import jakarta.persistence.LockModeType;

/**
 * 회원 계정 저장소
 */
public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    /**
     * 삭제되지 않은 계정 ID 조회
     *
     * @param id 사용자 ID
     * @return 계정, 없거나 삭제됐으면 빈 값
     */
    Optional<UserEntity> findByIdAndDeletedAtIsNull(UUID id);

    /**
     * 삭제되지 않은 계정 행 잠금 조회
     *
     * 같은 회원의 동시 요청을 한 줄로 세울 때 사용
     *
     * @return 잠근 계정, 없거나 삭제됐으면 빈 값
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.id = :id and u.deletedAt is null")
    Optional<UserEntity> findForUpdate(UUID id);

    /**
     * 삭제되지 않은 계정 아이디 조회
     *
     * @param username 정규화된 아이디
     * @return 계정, 없거나 삭제됐으면 빈 값
     */
    Optional<UserEntity> findByUsernameAndDeletedAtIsNull(String username);

    /**
     * 해당 권한을 가진 삭제되지 않은 계정 하나 조회
     *
     * @param role 권한
     * @return 계정 하나(순서 보장 없음), 없으면 빈 값
     */
    Optional<UserEntity> findFirstByRoleAndDeletedAtIsNull(UserRole role);
}

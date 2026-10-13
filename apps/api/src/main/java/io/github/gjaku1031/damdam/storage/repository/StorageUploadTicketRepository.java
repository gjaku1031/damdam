package io.github.gjaku1031.damdam.storage.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import io.github.gjaku1031.damdam.storage.domain.StorageUploadTicketEntity;
import io.github.gjaku1031.damdam.storage.domain.StorageUploadTicketStatus;
import jakarta.persistence.LockModeType;

/**
 * 업로드 티켓 저장소
 */
public interface StorageUploadTicketRepository extends JpaRepository<StorageUploadTicketEntity, UUID> {
    /**
     * 티켓 잠금 조회
     *
     * @return 티켓, 없으면 빈 값
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from StorageUploadTicketEntity t where t.id = :id")
    Optional<StorageUploadTicketEntity> findForUpdate(UUID id);

    /**
     * 저장 키와 상태가 일치하는 티켓 공유 잠금 조회
     *
     * 사진을 연결하는 트랜잭션이 끝날 때까지 정리 작업이 같은 티켓을 지우지 못하게 막을 때 사용
     * 공유 잠금이라 같은 사진을 연결하는 요청끼리는 서로 기다리지 않음
     *
     * @return 잠근 티켓, 없으면 빈 값
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select t from StorageUploadTicketEntity t where t.objectKey = :objectKey and t.status = :status")
    Optional<StorageUploadTicketEntity> findForShare(String objectKey, StorageUploadTicketStatus status);
}

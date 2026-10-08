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
     * 저장 키와 상태가 일치하는 티켓 존재 여부
     *
     * @return 존재 여부
     */
    boolean existsByObjectKeyAndStatus(String objectKey, StorageUploadTicketStatus status);
}

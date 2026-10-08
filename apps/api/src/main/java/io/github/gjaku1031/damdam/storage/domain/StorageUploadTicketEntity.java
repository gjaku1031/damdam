package io.github.gjaku1031.damdam.storage.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import io.github.gjaku1031.damdam.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 두 단계 사진 업로드 티켓
 *
 * 1. 발급 시 예상 MIME·크기·만료 시각 기록
 * 2. 실제 업로드에서 같은 값인지 검증 후 VERIFIED, 만료되면 FAILED
 */
@Getter
@Entity
@Table(name = "storage_upload_tickets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StorageUploadTicketEntity extends BaseEntity {
    /**
     * 발급받은 운영자 ID, 업로드도 같은 사용자만 가능
     */
    @Column(name = "issued_by_user_id", nullable = false)
    private UUID issuedByUserId;

    /**
     * 사진을 쓸 도메인
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UploadDomain domain;

    /**
     * 저장 키({@code post|product/<uuid>.<ext>}), 유일
     */
    @Column(nullable = false, unique = true)
    private String objectKey;

    /**
     * 예상 파일 크기(바이트)
     */
    @Column(nullable = false)
    private int expectedSizeBytes;

    /**
     * 예상 MIME 타입
     */
    @Column(nullable = false)
    private String expectedContentType;

    /**
     * 티켓 상태
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StorageUploadTicketStatus status = StorageUploadTicketStatus.ISSUED;

    /**
     * 만료 시각
     */
    @Column(nullable = false)
    private Instant expiresAt;

    /**
     * 검증 완료 시각
     */
    private Instant verifiedAt;

    /**
     * 실패 처리 시각
     */
    private Instant failedAt;

    /**
     * 현재 시각부터 TTL 동안 유효한 티켓 발급
     *
     * @return 저장 전 티켓
     */
    public static StorageUploadTicketEntity issue(UUID userId, UploadDomain domain, String objectKey, int sizeBytes,
            String contentType, Duration ttl) {
        StorageUploadTicketEntity ticket = new StorageUploadTicketEntity();
        ticket.issuedByUserId = userId;
        ticket.domain = domain;
        ticket.objectKey = objectKey;
        ticket.expectedSizeBytes = sizeBytes;
        ticket.expectedContentType = contentType;
        ticket.expiresAt = Instant.now().plus(ttl);
        return ticket;
    }

    /**
     * 업로드 가능 여부
     *
     * @return 발급 상태이고 만료 전이면 {@code true}
     */
    public boolean isUsable(Instant now) {
        return status == StorageUploadTicketStatus.ISSUED && !expiresAt.isBefore(now);
    }

    /**
     * 검증 완료, 실패 시각은 비움
     */
    public void markVerified() {
        status = StorageUploadTicketStatus.VERIFIED;
        verifiedAt = Instant.now();
        failedAt = null;
    }

    /**
     * 만료·검증 실패로 사용 불가 처리
     */
    public void markFailed() {
        status = StorageUploadTicketStatus.FAILED;
        failedAt = Instant.now();
    }
}

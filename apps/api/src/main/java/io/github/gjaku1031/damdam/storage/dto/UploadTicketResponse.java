package io.github.gjaku1031.damdam.storage.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.gjaku1031.damdam.storage.domain.StorageUploadTicketEntity;
import io.github.gjaku1031.damdam.storage.domain.StorageUploadTicketStatus;
import io.github.gjaku1031.damdam.storage.domain.UploadDomain;

/**
 * 업로드 티켓 응답
 *
 * @param id 티켓 ID, 업로드 경로에 사용
 * @param createdAt 발급 시각
 * @param updatedAt 수정 시각
 * @param issuedByUserId 발급받은 운영자 ID
 * @param domain 사진을 쓸 도메인
 * @param objectKey 저장 키
 * @param expectedSizeBytes 신고한 파일 크기(바이트)
 * @param expectedContentType 신고한 MIME 형식
 * @param status 티켓 상태
 * @param expiresAt 만료 시각
 * @param verifiedAt 업로드 검증 시각, 검증 전이면 {@code null}
 * @param failedAt 실패 처리 시각, 실패 전이면 {@code null}
 */
public record UploadTicketResponse(UUID id, Instant createdAt, Instant updatedAt, UUID issuedByUserId,
        UploadDomain domain, String objectKey, int expectedSizeBytes, String expectedContentType,
        StorageUploadTicketStatus status, Instant expiresAt, Instant verifiedAt, Instant failedAt) {
    /**
     * 티켓 엔티티로 생성
     *
     * @return 티켓 응답
     */
    public static UploadTicketResponse from(StorageUploadTicketEntity ticket) {
        return new UploadTicketResponse(ticket.getId(), ticket.getCreatedAt(), ticket.getUpdatedAt(),
                ticket.getIssuedByUserId(), ticket.getDomain(), ticket.getObjectKey(), ticket.getExpectedSizeBytes(),
                ticket.getExpectedContentType(), ticket.getStatus(), ticket.getExpiresAt(), ticket.getVerifiedAt(),
                ticket.getFailedAt());
    }
}

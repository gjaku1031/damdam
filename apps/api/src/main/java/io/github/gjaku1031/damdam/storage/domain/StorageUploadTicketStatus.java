package io.github.gjaku1031.damdam.storage.domain;

/**
 * 업로드 티켓 상태
 */
public enum StorageUploadTicketStatus {
    /**
     * 발급, 업로드 전
     */
    ISSUED,

    /**
     * 업로드·검증 완료
     */
    VERIFIED,

    /**
     * 만료 또는 검증 실패
     */
    FAILED
}

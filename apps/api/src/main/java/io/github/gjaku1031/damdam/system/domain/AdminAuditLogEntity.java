package io.github.gjaku1031.damdam.system.domain;

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
 * 운영자 변경 기록
 */
@Getter
@Entity
@Table(name = "admin_audit_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditLogEntity extends BaseEntity {
    /**
     * 운영자 ID
     */
    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    /**
     * 동작
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdminAuditAction action;

    /**
     * 대상 종류(PRODUCT, POST, ORDERS 등)
     */
    @Column(nullable = false)
    private String targetType;

    /**
     * 대상 ID 또는 하위 경로, 없으면 {@code null}
     */
    private String targetId;

    /**
     * 화면 표시용 설명
     */
    @Column(length = 500)
    private String description;

    /**
     * 기록 생성
     *
     * @return 저장 전 기록
     */
    public static AdminAuditLogEntity of(UUID actorUserId, AdminAuditAction action, String targetType,
            String targetId, String description) {
        AdminAuditLogEntity log = new AdminAuditLogEntity();
        log.actorUserId = actorUserId;
        log.action = action;
        log.targetType = targetType;
        log.targetId = targetId;
        log.description = description;
        return log;
    }
}

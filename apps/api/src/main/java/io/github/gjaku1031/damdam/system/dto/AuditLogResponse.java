package io.github.gjaku1031.damdam.system.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.gjaku1031.damdam.system.domain.AdminAuditAction;

/**
 * 운영 기록 응답
 *
 * @param id 기록 ID
 * @param createdAt 기록 시각
 * @param updatedAt 수정 시각
 * @param actorUserId 작업한 운영자 ID
 * @param actorUsername 작업한 운영자 아이디, 계정을 찾을 수 없으면 {@code null}
 * @param action 작업 종류
 * @param targetType 대상 종류
 * @param targetId 대상 ID, 없으면 {@code null}
 * @param description 작업 설명
 */
public record AuditLogResponse(UUID id, Instant createdAt, Instant updatedAt, UUID actorUserId, String actorUsername,
        AdminAuditAction action, String targetType, String targetId, String description) {}

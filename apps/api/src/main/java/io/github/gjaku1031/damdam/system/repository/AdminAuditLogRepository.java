package io.github.gjaku1031.damdam.system.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.gjaku1031.damdam.system.domain.AdminAuditLogEntity;

/**
 * 운영 기록 저장소
 */
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLogEntity, UUID> {}

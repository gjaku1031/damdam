package io.github.gjaku1031.damdam.system.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.gjaku1031.damdam.system.domain.SystemConfigEntity;

/**
 * 운영 설정 저장소, 설정 키가 ID
 */
public interface SystemConfigRepository extends JpaRepository<SystemConfigEntity, String> {}

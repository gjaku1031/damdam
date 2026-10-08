package io.github.gjaku1031.damdam.system.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.gjaku1031.damdam.system.domain.SystemConfigEntity;

/**
 * 운영 설정 응답
 *
 * @param configKey 설정 키
 * @param configValue 설정 값
 * @param description 설정 설명
 * @param updatedByUserId 마지막으로 수정한 운영자 ID, 기본값이면 {@code null}
 * @param updatedAt 수정 시각
 */
public record SystemConfigResponse(String configKey, String configValue, String description, UUID updatedByUserId,
        Instant updatedAt) {
    /**
     * 설정 엔티티에서 생성
     *
     * @param config 운영 설정
     * @return 설정 응답
     */
    public static SystemConfigResponse from(SystemConfigEntity config) {
        return new SystemConfigResponse(config.getConfigKey(), config.getConfigValue(), config.getDescription(),
                config.getUpdatedByUserId(), config.getUpdatedAt());
    }
}

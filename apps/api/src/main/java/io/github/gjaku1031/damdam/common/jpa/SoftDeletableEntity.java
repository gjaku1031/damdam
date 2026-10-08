package io.github.gjaku1031.damdam.common.jpa;

import java.time.Instant;

import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/**
 * 행을 지우지 않고 삭제 시각만 기록하는 엔티티 상위 타입
 *
 * 조회 쿼리에서 {@code deletedAt IS NULL}을 직접 조건으로 지정
 */
@Getter
@MappedSuperclass
public abstract class SoftDeletableEntity extends BaseEntity {
    /**
     * 삭제 시각, 삭제 전이면 {@code null}
     */
    private Instant deletedAt;

    /**
     * 삭제 시각 기록, 이미 삭제됐으면 유지
     */
    public void softDelete() {
        if (deletedAt == null) deletedAt = Instant.now();
    }

    /**
     * 삭제 여부
     *
     * @return 삭제 시각이 있으면 {@code true}
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }
}

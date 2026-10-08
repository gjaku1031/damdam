package io.github.gjaku1031.damdam.common.jpa;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * UUID 식별자와 생성·수정 시각을 가진 엔티티 공통 상위 타입
 *
 * ID는 애플리케이션에서 생성
 * 외부에서 정한 ID(운영자 계정 ID 유지 등)도 저장할 수 있도록 {@link Persistable#isNew()}를 직접 관리해
 * Spring Data가 새 엔티티를 merge하지 않고 persist하게 함
 */
@Getter
@MappedSuperclass
public abstract class BaseEntity implements Persistable<UUID> {
    /**
     * ID
     */
    @Id
    private UUID id = UUID.randomUUID();

    /**
     * 생성 시각, 저장 시 비어 있으면 현재 시각
     */
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * 수정 시각, 변경 감지로 갱신할 때마다 현재 시각
     */
    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * 아직 저장하지 않은 엔티티 여부
     */
    @Transient
    @Getter(AccessLevel.NONE)
    private boolean fresh = true;

    /**
     * 외부에서 정한 ID 지정
     *
     * 저장 전에만 호출
     */
    protected void assignId(UUID id) {
        this.id = id;
    }

    /**
     * 저장 전 엔티티 여부
     *
     * @return 처음 저장할 엔티티면 {@code true}
     */
    @Override
    public boolean isNew() {
        return fresh;
    }

    /**
     * 최초 저장 시 생성·수정 시각 기록
     */
    @PrePersist
    protected void beforeInsert() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    /**
     * 변경 저장 시 수정 시각 갱신
     */
    @PreUpdate
    protected void beforeUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * 조회·저장 이후 저장된 엔티티로 표시
     */
    @PostLoad
    @PostPersist
    protected void markPersisted() {
        fresh = false;
    }
}

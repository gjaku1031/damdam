package io.github.gjaku1031.damdam.inventory.domain;

import java.util.UUID;

import io.github.gjaku1031.damdam.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 재고 증감 기록
 */
@Getter
@Entity
@Table(name = "inventory_adjustments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryAdjustmentEntity extends BaseEntity {
    /**
     * 상품 옵션 ID
     */
    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    /**
     * 조정한 사용자 ID, 시스템·탈퇴 회원이면 {@code null}
     */
    @Column(name = "actor_user_id")
    private UUID actorUserId;

    /**
     * 증감 수량, 양수면 입고·음수면 출고
     */
    @Column(nullable = false)
    private int delta;

    /**
     * 사유
     */
    @Column(nullable = false)
    private String reason;

    /**
     * 조정 기록 생성
     *
     * @return 저장 전 기록
     */
    public static InventoryAdjustmentEntity of(UUID variantId, UUID actorUserId, int delta, String reason) {
        InventoryAdjustmentEntity adjustment = new InventoryAdjustmentEntity();
        adjustment.variantId = variantId;
        adjustment.actorUserId = actorUserId;
        adjustment.delta = delta;
        adjustment.reason = reason;
        return adjustment;
    }
}

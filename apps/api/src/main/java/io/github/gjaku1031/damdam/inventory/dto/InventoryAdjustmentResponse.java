package io.github.gjaku1031.damdam.inventory.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.gjaku1031.damdam.inventory.domain.InventoryAdjustmentEntity;

/**
 * 재고 조정 기록 응답
 *
 * @param id ID
 * @param createdAt 조정 시각
 * @param updatedAt 수정 시각
 * @param variantId 옵션 ID
 * @param actorUserId 조정한 운영자 ID
 * @param delta 재고 변화량
 * @param reason 조정 사유
 */
public record InventoryAdjustmentResponse(UUID id, Instant createdAt, Instant updatedAt, UUID variantId,
        UUID actorUserId, int delta, String reason) {
    /**
     * 조정 기록 엔티티로 생성
     *
     * @return 조정 기록 응답
     */
    public static InventoryAdjustmentResponse from(InventoryAdjustmentEntity adjustment) {
        return new InventoryAdjustmentResponse(adjustment.getId(), adjustment.getCreatedAt(), adjustment.getUpdatedAt(),
                adjustment.getVariantId(), adjustment.getActorUserId(), adjustment.getDelta(), adjustment.getReason());
    }
}

package io.github.gjaku1031.damdam.inventory.service;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.gjaku1031.damdam.common.error.ApiException;
import io.github.gjaku1031.damdam.common.web.ItemPage;
import io.github.gjaku1031.damdam.common.web.Paging;
import io.github.gjaku1031.damdam.inventory.domain.InventoryAdjustmentEntity;
import io.github.gjaku1031.damdam.inventory.dto.AdjustmentResult;
import io.github.gjaku1031.damdam.inventory.dto.InventoryAdjustmentResponse;
import io.github.gjaku1031.damdam.inventory.repository.InventoryAdjustmentRepository;
import io.github.gjaku1031.damdam.product.domain.ProductVariantEntity;
import io.github.gjaku1031.damdam.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 재고 조정과 조정 이력
 *
 * 재고 변경은 항상 옵션 행을 잠근 뒤 조정 기록과 함께 한 트랜잭션에서 반영
 */
@Service
@RequiredArgsConstructor
public class InventoryService {
    /**
     * 상품 옵션 저장소
     */
    private final ProductVariantRepository variants;

    /**
     * 재고 조정 기록 저장소
     */
    private final InventoryAdjustmentRepository adjustments;

    /**
     * 재고 조정
     *
     * 하나의 트랜잭션에서 실행, 실패 시 재고 변경과 조정 기록이 함께 롤백됨
     *
     * 1. 옵션 행 잠금(동시 주문·조정과 재고 계산 충돌 방지)
     * 2. 조정 후 재고가 음수가 되는지 확인
     * 3. 재고 반영 후 조정 기록 저장
     *
     * @param delta 재고 변화량, 양수는 입고·음수는 출고
     * @param actorUserId 조정한 운영자 ID
     * @return 저장한 기록과 조정 후 재고
     * @throws ApiException 옵션이 없을 때(404), 재고가 음수가 될 때(400)
     */
    @Transactional
    public AdjustmentResult adjust(UUID variantId, int delta, String reason, UUID actorUserId) {
        // 잠금 조회 후 음수 재고 거부
        ProductVariantEntity variant = variants.findForUpdate(variantId).orElseThrow(ApiException::notFound);
        if (variant.getStock() + delta < 0) throw ApiException.badRequest("재고는 음수가 될 수 없습니다.");
        // 재고 반영과 이력 기록
        variant.adjustStock(delta);
        InventoryAdjustmentEntity adjustment =
                adjustments.save(InventoryAdjustmentEntity.of(variantId, actorUserId, delta, reason));
        return new AdjustmentResult(InventoryAdjustmentResponse.from(adjustment), variant.getStock());
    }

    /**
     * 옵션의 재고 조정 이력 조회
     *
     * @return 최근 조정 순(생성 시각·ID 내림차순) 기록 쪽
     */
    @Transactional(readOnly = true)
    public ItemPage<InventoryAdjustmentResponse> history(UUID variantId, Paging paging) {
        var page = adjustments.findByVariantId(variantId, PageRequest.of(paging.page() - 1, paging.size(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return ItemPage.of(page.map(InventoryAdjustmentResponse::from).getContent(), page.getTotalElements(), paging);
    }
}

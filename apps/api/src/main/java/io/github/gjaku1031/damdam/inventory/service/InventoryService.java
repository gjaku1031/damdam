package io.github.gjaku1031.damdam.inventory.service;

import static io.github.gjaku1031.damdam.jooq.Tables.INVENTORY_RESERVATIONS;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.gjaku1031.damdam.common.error.ApiException;
import io.github.gjaku1031.damdam.common.web.ItemPage;
import io.github.gjaku1031.damdam.common.web.Paging;
import io.github.gjaku1031.damdam.inventory.domain.InventoryAdjustmentEntity;
import io.github.gjaku1031.damdam.inventory.domain.InventoryReservationEntity;
import io.github.gjaku1031.damdam.inventory.domain.InventoryReservationStatus;
import io.github.gjaku1031.damdam.inventory.dto.AdjustmentResult;
import io.github.gjaku1031.damdam.inventory.dto.InventoryAdjustmentResponse;
import io.github.gjaku1031.damdam.inventory.repository.InventoryAdjustmentRepository;
import io.github.gjaku1031.damdam.inventory.repository.InventoryReservationRepository;
import io.github.gjaku1031.damdam.product.domain.ProductVariantEntity;
import io.github.gjaku1031.damdam.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;

/**
 * 재고 조정 이력과 예약 재고
 *
 * 재고 변경은 항상 옵션 행을 잠근 뒤 조정 기록과 함께 한 트랜잭션에서 반영
 * 예약은 결제 대기 주문이 재고를 무기한 점유하지 않도록 TTL 이후 만료
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
     * 재고 예약 저장소
     */
    private final InventoryReservationRepository reservations;

    /**
     * 예약 일괄 만료용 jOOQ
     */
    private final DSLContext dsl;

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

    /**
     * 주문 재고 예약
     *
     * @param ttl 예약 유지 시간, 지나면 {@link #expireOverdue()}로 만료
     * @return 저장한 예약
     */
    @Transactional
    public InventoryReservationEntity reserve(UUID variantId, UUID orderId, int quantity, Duration ttl) {
        return reservations.save(InventoryReservationEntity.reserve(variantId, orderId, quantity, ttl));
    }

    /**
     * 예약 사용 처리
     *
     * @throws ApiException 예약이 없을 때(404)
     */
    @Transactional
    public void consumeReservation(UUID id) {
        reservations.findById(id).orElseThrow(ApiException::notFound).consume();
    }

    /**
     * 예약 취소
     *
     * @throws ApiException 예약이 없을 때(404)
     */
    @Transactional
    public void cancelReservation(UUID id) {
        reservations.findById(id).orElseThrow(ApiException::notFound).cancel();
    }

    /**
     * 결제 없이 TTL이 지난 활성 예약 일괄 만료
     *
     * @return 만료 처리한 예약 수
     */
    @Transactional
    public int expireOverdue() {
        Instant now = Instant.now();
        return dsl.update(INVENTORY_RESERVATIONS)
                .set(INVENTORY_RESERVATIONS.STATUS, InventoryReservationStatus.EXPIRED.name())
                .set(INVENTORY_RESERVATIONS.UPDATEDAT, now)
                .where(INVENTORY_RESERVATIONS.STATUS.eq(InventoryReservationStatus.ACTIVE.name()))
                .and(INVENTORY_RESERVATIONS.EXPIRESAT.le(now))
                .execute();
    }
}

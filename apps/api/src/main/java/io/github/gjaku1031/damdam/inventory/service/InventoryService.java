package io.github.gjaku1031.damdam.inventory.service;

import static io.github.gjaku1031.damdam.jooq.Tables.INVENTORY_ADJUSTMENTS;
import static io.github.gjaku1031.damdam.jooq.Tables.PRODUCTS;
import static io.github.gjaku1031.damdam.jooq.Tables.PRODUCT_VARIANTS;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
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
import io.github.gjaku1031.damdam.inventory.dto.StockLedgerMismatch;
import io.github.gjaku1031.damdam.inventory.repository.InventoryAdjustmentRepository;
import io.github.gjaku1031.damdam.product.domain.ProductVariantEntity;
import io.github.gjaku1031.damdam.product.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 재고 조정과 조정 이력, 재고와 이력의 대조
 *
 * 재고 변경은 항상 옵션 행을 잠근 뒤 조정 기록과 함께 한 트랜잭션에서 반영
 */
@Service
@RequiredArgsConstructor
public class InventoryService {
    /**
     * 대조 결과 최대 개수, 정상이면 0건이라 응답 크기만 제한
     */
    private static final int MISMATCH_LIMIT = 50;

    /**
     * 상품 옵션 저장소
     */
    private final ProductVariantRepository variants;

    /**
     * 재고 조정 기록 저장소
     */
    private final InventoryAdjustmentRepository adjustments;

    /**
     * 재고·이력 대조용 jOOQ
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
     * 현재 재고와 재고 이력 합계가 다른 옵션 조회
     *
     * 재고를 바꾸는 모든 경로가 같은 트랜잭션에서 이력을 남기므로 정상이면 빈 목록
     * 어긋난 옵션이 있으면 이력 없이 재고를 바꾼 코드가 있거나 DB를 직접 고친 것
     * 재고와 이력을 한 문장으로 읽어, 진행 중인 주문·조정의 중간 상태를 불일치로 잡지 않음
     *
     * @return 삭제된 상품의 옵션을 포함해 상품명·옵션명 순, 최대 {@value #MISMATCH_LIMIT}개
     */
    @Transactional(readOnly = true)
    public List<StockLedgerMismatch> ledgerMismatches() {
        Field<BigDecimal> ledgerTotal = DSL.coalesce(DSL.sum(INVENTORY_ADJUSTMENTS.DELTA), BigDecimal.ZERO);
        return dsl.select(PRODUCTS.ID, PRODUCTS.NAME, PRODUCT_VARIANTS.ID, PRODUCT_VARIANTS.NAME, PRODUCT_VARIANTS.STOCK,
                        ledgerTotal)
                .from(PRODUCT_VARIANTS)
                .join(PRODUCTS).on(PRODUCTS.ID.eq(PRODUCT_VARIANTS.PRODUCTID))
                .leftJoin(INVENTORY_ADJUSTMENTS).on(INVENTORY_ADJUSTMENTS.VARIANT_ID.eq(PRODUCT_VARIANTS.ID))
                .groupBy(PRODUCTS.ID, PRODUCTS.NAME, PRODUCT_VARIANTS.ID, PRODUCT_VARIANTS.NAME, PRODUCT_VARIANTS.STOCK)
                .having(ledgerTotal.ne(PRODUCT_VARIANTS.STOCK.coerce(BigDecimal.class)))
                .orderBy(PRODUCTS.NAME.asc(), PRODUCT_VARIANTS.NAME.asc(), PRODUCT_VARIANTS.ID.asc())
                .limit(MISMATCH_LIMIT)
                .fetch(row -> new StockLedgerMismatch(row.value1(), row.value2(), row.value3(), row.value4(),
                        row.value5(), row.value6().longValue()));
    }
}

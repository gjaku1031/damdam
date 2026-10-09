package io.github.gjaku1031.damdam.product.service;

import static io.github.gjaku1031.damdam.jooq.Tables.PRODUCTS;
import static io.github.gjaku1031.damdam.jooq.Tables.PRODUCT_VARIANTS;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.gjaku1031.damdam.common.error.ApiException;
import io.github.gjaku1031.damdam.common.web.Paging;
import io.github.gjaku1031.damdam.product.domain.ProductEntity;
import io.github.gjaku1031.damdam.product.domain.ProductImageEntity;
import io.github.gjaku1031.damdam.product.domain.ProductSort;
import io.github.gjaku1031.damdam.product.domain.ProductVariantEntity;
import io.github.gjaku1031.damdam.product.dto.ImageInput;
import io.github.gjaku1031.damdam.product.dto.ProductChanges;
import io.github.gjaku1031.damdam.product.dto.ProductImageResponse;
import io.github.gjaku1031.damdam.product.dto.ProductPage;
import io.github.gjaku1031.damdam.product.dto.ProductResponse;
import io.github.gjaku1031.damdam.product.dto.ProductVariantResponse;
import io.github.gjaku1031.damdam.product.dto.UpdateProductRequest;
import io.github.gjaku1031.damdam.product.dto.VariantInput;
import io.github.gjaku1031.damdam.product.repository.ProductImageRepository;
import io.github.gjaku1031.damdam.product.repository.ProductRepository;
import io.github.gjaku1031.damdam.product.repository.ProductVariantRepository;
import io.github.gjaku1031.damdam.storage.service.StorageService;
import lombok.RequiredArgsConstructor;

/**
 * 상품·옵션·사진 조회와 관리
 *
 * 옵션은 지우지 않고 판매 중지로 남겨 과거 주문이 참조 가능
 * 기존 옵션의 재고는 상품 수정으로 바꿀 수 없고 재고 조정(이력 기록)으로만 변경
 */
@Service
@RequiredArgsConstructor
public class ProductService {
    /**
     * 이름 정렬 콜레이션, 서버 로케일과 관계없이 한글 음절을 가나다순으로 정렬
     */
    private static final String CODE_POINT = "C";

    /**
     * 상품 저장소
     */
    private final ProductRepository products;

    /**
     * 옵션 저장소
     */
    private final ProductVariantRepository variants;

    /**
     * 사진 저장소
     */
    private final ProductImageRepository images;

    /**
     * 상품 분류 서비스
     */
    private final ProductCategoryService categories;

    /**
     * 업로드 사진 검증
     */
    private final StorageService storage;

    /**
     * 목록·건수 조회용 jOOQ
     */
    private final DSLContext dsl;

    /**
     * 저장 후 별도 조회를 위한 트랜잭션 실행기
     */
    private final TransactionTemplate transaction;

    /**
     * 상품 목록 조회
     *
     * 이름 정렬은 코드 포인트(한글 가나다) 순, 가격 정렬은 판매 중인 옵션 중 기본 옵션(없으면 최저가) 가격 기준, 판매 옵션이 없는 상품은 맨 뒤
     *
     * @param visibleOnly {@code true}면 공개 상품과 판매 중인 옵션만
     * @param search 상품명 부분 일치(대소문자 무시), 비어 있으면 전체
     * @param category 분류, 앞뒤 공백을 무시하고 정확히 일치, 비어 있으면 전체
     * @param sort 정렬 기준
     * @return 정렬 기준 순 상품 쪽, 같은 순위는 최근 등록 순
     */
    @Transactional(readOnly = true)
    public ProductPage list(boolean visibleOnly, Paging paging, String search, String category, ProductSort sort) {
        // 조건: 삭제 제외, 공개 여부, 이름 검색, 분류(저장값 앞뒤 공백 무시)
        Condition condition = PRODUCTS.DELETEDAT.isNull();
        if (visibleOnly) condition = condition.and(PRODUCTS.VISIBLE.isTrue());
        if (search != null && !search.isEmpty()) condition = condition.and(PRODUCTS.NAME.likeIgnoreCase("%" + search + "%"));
        if (category != null && !category.isBlank()) {
            condition = condition.and(DSL.trim(PRODUCTS.CATEGORY).eq(category.trim()));
        }

        // 대표 가격: 판매 중인 옵션 중 기본 옵션 우선, 없으면 최저가
        Field<Integer> price = DSL.field(DSL.select(PRODUCT_VARIANTS.PRICEAMOUNT).from(PRODUCT_VARIANTS)
                .where(PRODUCT_VARIANTS.PRODUCTID.eq(PRODUCTS.ID), PRODUCT_VARIANTS.ACTIVE.isTrue())
                .orderBy(PRODUCT_VARIANTS.ISDEFAULT.desc(), PRODUCT_VARIANTS.PRICEAMOUNT.asc()).limit(1));
        SortField<?> primary = switch (sort) {
            case NEWEST -> PRODUCTS.CREATEDAT.desc();
            case PRICE_ASC -> price.asc().nullsLast();
            case PRICE_DESC -> price.desc().nullsLast();
            case NAME -> PRODUCTS.NAME.collate(CODE_POINT).asc();
        };

        // 쪽에 해당하는 ID를 먼저 고른 뒤 사진·옵션을 일괄 조회
        List<UUID> ids = dsl.select(PRODUCTS.ID).from(PRODUCTS).where(condition)
                .orderBy(primary, PRODUCTS.CREATEDAT.desc(), PRODUCTS.ID.desc())
                .limit(paging.size()).offset(paging.offset())
                .fetch(PRODUCTS.ID);
        return new ProductPage(responses(ids, visibleOnly), dsl.fetchCount(PRODUCTS, condition), paging.page(),
                paging.size());
    }

    /**
     * 공개 상품 분류 목록 조회
     *
     * @return 공개·삭제되지 않은 상품의 앞뒤 공백을 제거한 분류, 가나다순(코드 포인트)·중복 제거
     */
    @Transactional(readOnly = true)
    public List<String> categories() {
        // DISTINCT 정렬식은 선택 목록과 같아야 하므로 콜레이션을 선택식에 포함
        Field<String> name = DSL.trim(PRODUCTS.CATEGORY).collate(CODE_POINT);
        return dsl.selectDistinct(name).from(PRODUCTS)
                .where(PRODUCTS.DELETEDAT.isNull(), PRODUCTS.VISIBLE.isTrue(), name.ne(""))
                .orderBy(name)
                .fetch(name);
    }

    /**
     * 상품 상세 조회
     *
     * @param visibleOnly {@code true}면 공개 상품만 찾고 판매 중인 옵션만 담음
     * @return 사진(표시 순서)·옵션을 포함한 상품
     * @throws ApiException 상품이 없거나 삭제·비공개일 때(404)
     */
    @Transactional(readOnly = true)
    public ProductResponse get(UUID id, boolean visibleOnly) {
        ProductEntity product = products.findByIdAndDeletedAtIsNull(id)
                .filter(found -> !visibleOnly || found.isVisible())
                .orElseThrow(() -> ApiException.notFound("상품을 찾을 수 없습니다."));
        return responses(List.of(product.getId()), visibleOnly).getFirst();
    }

    /**
     * 상품 등록
     *
     * @return 등록한 상품(전체 옵션 포함)
     */
    public ProductResponse create(ProductChanges changes) {
        return save(null, changes);
    }

    /**
     * 상품 부분 수정
     *
     * @param changes {@code null} 항목은 유지
     * @return 수정한 상품(전체 옵션 포함)
     */
    public ProductResponse update(UUID id, ProductChanges changes) {
        return save(id, changes);
    }

    /**
     * 공개 여부만 변경
     *
     * @return 변경한 상품
     */
    public ProductResponse setVisible(UUID id, boolean visible) {
        return save(id, UpdateProductRequest.visibility(visible));
    }

    /**
     * 상품 소프트 삭제
     *
     * @throws ApiException 상품이 없거나 이미 삭제됐을 때(404)
     */
    @Transactional
    public void remove(UUID id) {
        ProductEntity product = products.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("상품을 찾을 수 없습니다."));
        product.softDelete();
    }

    /**
     * 상품 등록·수정 공통 처리
     *
     * 1. 사진 키가 검증된 업로드인지 트랜잭션 밖에서 먼저 확인
     * 2. 기존 상품이면 행 잠금 후 필드 반영, 옵션·사진 교체
     * 3. 커밋 후 응답용으로 다시 조회
     *
     * 2단계 실패 시 상품·옵션·사진 변경이 함께 롤백됨
     *
     * @param id 수정할 상품 ID, 등록이면 {@code null}
     * @return 저장 결과
     * @throws ApiException 사진·옵션·이름이 잘못됐을 때(400), 기존 옵션 재고 변경 시도(409), 상품 없음(404)
     */
    private ProductResponse save(UUID id, ProductChanges changes) {
        if (changes.images() != null) changes.images().forEach(image -> storage.assertUsable(image.objectKey()));
        // 분류는 운영자가 먼저 만든 것만 지정
        if (changes.category() != null && !changes.category().isBlank()
                && !categories.exists(changes.category().trim())) {
            throw ApiException.badRequest("등록된 분류를 선택해주세요.");
        }
        UUID savedId = transaction.execute(status -> {
            ProductEntity product = id == null ? ProductEntity.draft()
                    : products.findForUpdate(id).orElseThrow(ApiException::notFound);
            applyFields(product, changes);
            products.saveAndFlush(product);
            if (changes.variants() != null) replaceVariants(product.getId(), changes.variants());
            if (changes.images() != null) replaceImages(product.getId(), changes.images());
            return product.getId();
        });
        return get(savedId, false);
    }

    /**
     * 상품 필드 반영, {@code null} 항목은 유지
     *
     * @throws ApiException 이름이 비어 있을 때(400)
     */
    private static void applyFields(ProductEntity product, ProductChanges changes) {
        if (changes.name() != null) {
            if (changes.name().isBlank()) throw ApiException.badRequest("상품명을 입력해주세요.");
            product.rename(changes.name().trim());
        }
        if (changes.description() != null) product.changeDescription(changes.description());
        // 빈 분류명은 분류 해제
        if (changes.category() != null) {
            String category = changes.category().trim();
            product.changeCategory(category.isEmpty() ? null : category);
        }
        if (changes.visible() != null) product.changeVisibility(changes.visible());
        if (product.getName() == null) throw ApiException.badRequest("상품명을 입력해주세요.");
    }

    /**
     * 옵션 목록 교체
     *
     * 1. 상품의 기존 옵션을 ID 순서로 잠금
     * 2. 입력 ID가 중복 없이 기존 옵션만 가리키는지 확인
     * 3. 입력 순서대로 기존 옵션 수정 또는 새 옵션 생성, 기본 옵션 미지정이면 첫 옵션이 기본
     * 4. 기본 옵션 하나·판매 중 옵션 존재 검증 후, 입력에 없는 기존 옵션은 판매 중지
     *
     * 판매 중지 변경을 먼저 flush한 뒤 새 옵션을 저장해 옵션명 유일 제약과의 충돌 방지
     *
     * @throws ApiException 옵션 ID 오류·기본 옵션 규칙 위반(400), 기존 옵션 재고 변경 시도(409)
     */
    private void replaceVariants(UUID productId, List<VariantInput> inputs) {
        Map<UUID, ProductVariantEntity> existing = variants.findByProductForUpdate(productId).stream()
                .collect(Collectors.toMap(ProductVariantEntity::getId, Function.identity()));
        List<UUID> suppliedIds = inputs.stream().map(VariantInput::id).filter(Objects::nonNull).toList();
        if (new HashSet<>(suppliedIds).size() != suppliedIds.size() || !existing.keySet().containsAll(suppliedIds)) {
            throw ApiException.badRequest("상품 옵션을 확인해주세요.");
        }

        // 입력 순서대로 수정·생성
        boolean anyDefault = inputs.stream().anyMatch(input -> Boolean.TRUE.equals(input.isDefault()));
        List<ProductVariantEntity> next = new ArrayList<>();
        for (int index = 0; index < inputs.size(); index++) {
            VariantInput input = inputs.get(index);
            ProductVariantEntity variant = input.id() == null
                    ? ProductVariantEntity.create(productId, input.stock() == null ? 0 : input.stock())
                    : existing.get(input.id());
            if (input.id() != null && input.stock() != null && input.stock() != variant.getStock()) {
                throw ApiException.conflict("재고는 재고 조정에서 변경해주세요.");
            }
            variant.update(input.name().trim(), input.priceAmount(), input.active() == null || input.active(),
                    Boolean.TRUE.equals(input.isDefault()) || (index == 0 && !anyDefault));
            next.add(variant);
        }

        // 기본 옵션 하나, 판매 중 옵션 존재, 기본 옵션 판매 중
        long defaults = next.stream().filter(ProductVariantEntity::isDefault).count();
        boolean anyActive = next.stream().anyMatch(ProductVariantEntity::isActive);
        boolean defaultActive = next.stream().anyMatch(v -> v.isDefault() && v.isActive());
        if (defaults != 1 || !anyActive || !defaultActive) {
            throw ApiException.badRequest("기본 옵션 하나와 판매 중인 옵션이 필요합니다.");
        }

        // 입력에 없는 옵션은 과거 주문을 위해 남기고 판매 중지
        Set<UUID> kept = new HashSet<>(suppliedIds);
        existing.values().stream().filter(variant -> !kept.contains(variant.getId()))
                .forEach(ProductVariantEntity::retire);
        variants.flush();
        variants.saveAllAndFlush(next);
    }

    /**
     * 사진 목록 교체
     *
     * 표시 순서 유일 제약 때문에 기존 사진을 즉시 삭제한 뒤 입력 순서로 저장
     */
    private void replaceImages(UUID productId, List<ImageInput> inputs) {
        images.deleteAllOfProduct(productId);
        List<ProductImageEntity> next = new ArrayList<>();
        for (int sortOrder = 0; sortOrder < inputs.size(); sortOrder++) {
            next.add(ProductImageEntity.of(productId, inputs.get(sortOrder).objectKey(), sortOrder));
        }
        images.saveAllAndFlush(next);
    }

    /**
     * 상품 응답 일괄 조립
     *
     * 상품·사진·옵션을 각각 한 번씩 조회해 N+1 조회 방지
     *
     * @param ids 응답 순서대로의 상품 ID
     * @param activeVariantsOnly {@code true}면 판매 중인 옵션만
     * @return {@code ids} 순서의 응답
     */
    private List<ProductResponse> responses(List<UUID> ids, boolean activeVariantsOnly) {
        if (ids.isEmpty()) return List.of();
        Map<UUID, ProductEntity> byId = products.findAllById(ids).stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));
        Map<UUID, List<ProductImageResponse>> imagesByProduct = images.findByProductIdInOrderBySortOrderAsc(ids).stream()
                .collect(Collectors.groupingBy(ProductImageEntity::getProductId,
                        Collectors.mapping(ProductImageResponse::from, Collectors.toList())));
        Map<UUID, List<ProductVariantResponse>> variantsByProduct = variants.findByProductIdInOrderByCreatedAtAscIdAsc(ids)
                .stream().filter(variant -> !activeVariantsOnly || variant.isActive())
                .collect(Collectors.groupingBy(ProductVariantEntity::getProductId,
                        Collectors.mapping(ProductVariantResponse::from, Collectors.toList())));
        return ids.stream().map(byId::get).map(product -> ProductResponse.of(product,
                imagesByProduct.getOrDefault(product.getId(), List.of()),
                variantsByProduct.getOrDefault(product.getId(), List.of()))).toList();
    }
}

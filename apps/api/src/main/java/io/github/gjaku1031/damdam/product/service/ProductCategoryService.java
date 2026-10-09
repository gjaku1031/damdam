package io.github.gjaku1031.damdam.product.service;

import static io.github.gjaku1031.damdam.jooq.Tables.PRODUCTS;
import static io.github.gjaku1031.damdam.jooq.Tables.PRODUCT_CATEGORIES;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.gjaku1031.damdam.common.error.ApiException;
import io.github.gjaku1031.damdam.product.domain.ProductCategoryEntity;
import io.github.gjaku1031.damdam.product.dto.ProductCategoryResponse;
import io.github.gjaku1031.damdam.product.repository.ProductCategoryRepository;
import lombok.RequiredArgsConstructor;

/**
 * 상품 분류 관리
 */
@Service
@RequiredArgsConstructor
public class ProductCategoryService {
    /**
     * 코드 포인트 순 정렬 콜레이션
     */
    private static final String CODE_POINT = "C";

    /**
     * 분류 저장소
     */
    private final ProductCategoryRepository categories;

    /**
     * 목록 집계·상품 일괄 갱신용 jOOQ
     */
    private final DSLContext dsl;

    /**
     * 이름 중복을 트랜잭션 밖에서 변환하기 위한 실행기
     */
    private final TransactionTemplate transaction;

    /**
     * 전체 분류와 분류별 상품 수 조회
     *
     * @return 이름순(코드 포인트)
     */
    @Transactional(readOnly = true)
    public List<ProductCategoryResponse> list() {
        Field<Integer> count = DSL.field(DSL.selectCount().from(PRODUCTS)
                .where(PRODUCTS.CATEGORY.eq(PRODUCT_CATEGORIES.NAME), PRODUCTS.DELETEDAT.isNull()));
        return dsl.select(PRODUCT_CATEGORIES.ID, PRODUCT_CATEGORIES.NAME, count)
                .from(PRODUCT_CATEGORIES)
                .orderBy(PRODUCT_CATEGORIES.NAME.collate(CODE_POINT))
                .fetch(row -> new ProductCategoryResponse(row.value1(), row.value2(), row.value3()));
    }

    /**
     * 분류명 존재 여부
     *
     * @param name 앞뒤 공백을 제거한 이름
     * @return 등록된 분류면 {@code true}
     */
    @Transactional(readOnly = true)
    public boolean exists(String name) {
        return categories.existsByName(name);
    }

    /**
     * 분류 등록
     *
     * @return 등록한 분류
     * @throws ApiException 이름이 비어 있을 때(400), 이름이 중복될 때(409)
     */
    public ProductCategoryResponse create(String name) {
        String trimmed = required(name);
        return saving(() -> {
            ProductCategoryEntity category = categories.saveAndFlush(ProductCategoryEntity.named(trimmed));
            return new ProductCategoryResponse(category.getId(), category.getName(), 0);
        });
    }

    /**
     * 분류 이름 변경
     *
     * 같은 트랜잭션에서 이 분류를 쓰는 상품의 분류명도 함께 변경
     *
     * @return 변경한 분류
     * @throws ApiException 이름이 비어 있을 때(400), 분류가 없을 때(404), 이름이 중복될 때(409)
     */
    public ProductCategoryResponse rename(UUID id, String name) {
        String trimmed = required(name);
        return saving(() -> {
            ProductCategoryEntity category = find(id);
            String before = category.getName();
            category.rename(trimmed);
            categories.saveAndFlush(category);
            int moved = dsl.update(PRODUCTS).set(PRODUCTS.CATEGORY, trimmed)
                    .where(PRODUCTS.CATEGORY.eq(before)).execute();
            return new ProductCategoryResponse(id, trimmed, moved);
        });
    }

    /**
     * 분류 삭제
     *
     * 이 분류를 쓰던 상품은 분류 없음으로 바뀌고, 실패 시 함께 롤백
     *
     * @return 삭제한 분류명
     * @throws ApiException 분류가 없을 때(404)
     */
    @Transactional
    public String remove(UUID id) {
        ProductCategoryEntity category = find(id);
        dsl.update(PRODUCTS).setNull(PRODUCTS.CATEGORY).where(PRODUCTS.CATEGORY.eq(category.getName())).execute();
        categories.delete(category);
        return category.getName();
    }

    /**
     * 분류 조회
     *
     * @throws ApiException 분류가 없을 때(404)
     */
    private ProductCategoryEntity find(UUID id) {
        return categories.findById(id).orElseThrow(() -> ApiException.notFound("분류를 찾을 수 없습니다."));
    }

    /**
     * 트랜잭션 실행, 이름 중복은 409로 변환
     *
     * @throws ApiException 이름이 중복될 때(409)
     */
    private ProductCategoryResponse saving(Supplier<ProductCategoryResponse> work) {
        try {
            return transaction.execute(status -> work.get());
        } catch (DataIntegrityViolationException duplicate) {
            throw ApiException.conflict("이미 있는 분류입니다.");
        }
    }

    /**
     * 앞뒤 공백을 제거한 분류명
     *
     * @throws ApiException 비어 있을 때(400)
     */
    private static String required(String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) throw ApiException.badRequest("분류명을 입력해주세요.");
        return trimmed;
    }
}

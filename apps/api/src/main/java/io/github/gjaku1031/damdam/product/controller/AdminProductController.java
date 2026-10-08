package io.github.gjaku1031.damdam.product.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.gjaku1031.damdam.common.auth.CurrentUser;
import io.github.gjaku1031.damdam.common.web.Paging;
import io.github.gjaku1031.damdam.product.domain.ProductSort;
import io.github.gjaku1031.damdam.product.dto.CreateProductRequest;
import io.github.gjaku1031.damdam.product.dto.ProductPage;
import io.github.gjaku1031.damdam.product.dto.ProductResponse;
import io.github.gjaku1031.damdam.product.dto.UpdateProductRequest;
import io.github.gjaku1031.damdam.product.service.ProductService;
import io.github.gjaku1031.damdam.system.domain.AdminAuditAction;
import io.github.gjaku1031.damdam.system.service.SystemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 상품 관리 API
 *
 * 비공개·판매 중지 옵션까지 조회하며 변경 작업은 운영 감사 기록을 남김
 */
@RestController
@RequestMapping("/admin/products")
@RequiredArgsConstructor
public final class AdminProductController {
    /**
     * 감사 기록의 대상 유형
     */
    private static final String TARGET = "PRODUCT";

    /**
     * 상품 서비스
     */
    private final ProductService products;

    /**
     * 운영 감사 기록
     */
    private final SystemService audit;

    /**
     * 전체 상품 목록 조회
     *
     * @param page 쪽 번호, 생략 시 1
     * @param limit 쪽 크기, 생략 시 100
     * @param search 상품명 부분 일치 검색어, 생략 시 전체
     * @param category 분류 정확히 일치, 생략 시 전체
     * @param sort 정렬({@code newest}·{@code price_asc}·{@code price_desc}·{@code name}), 생략 시 {@code newest}
     * @return 비공개 상품과 판매 중지 옵션을 포함한 정렬 기준 순 상품 쪽
     */
    @GetMapping
    public ProductPage list(@RequestParam(required = false) String page, @RequestParam(required = false) String limit,
            @RequestParam(required = false) String search, @RequestParam(required = false) String category,
            @RequestParam(required = false) String sort) {
        return products.list(false, Paging.of(page, limit, 100), search, category, ProductSort.parse(sort));
    }

    /**
     * 상품 상세 조회
     *
     * @return 비공개 상품·판매 중지 옵션을 포함한 상품
     */
    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable UUID id) {
        return products.get(id, false);
    }

    /**
     * 상품 등록 후 감사 기록
     *
     * @return 등록한 상품
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody CreateProductRequest body, CurrentUser user) {
        ProductResponse product = products.create(body);
        audit.log(user.id(), AdminAuditAction.CREATE, TARGET, product.id().toString(), null);
        return product;
    }

    /**
     * 상품 부분 수정 후 감사 기록
     *
     * @return 수정한 상품
     */
    @PatchMapping("/{id}")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequest body,
            CurrentUser user) {
        ProductResponse product = products.update(id, body);
        audit.log(user.id(), AdminAuditAction.UPDATE, TARGET, id.toString(), null);
        return product;
    }

    /**
     * 상품 공개 후 감사 기록
     *
     * @return 공개한 상품
     */
    @PostMapping("/{id}/show")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse show(@PathVariable UUID id, CurrentUser user) {
        ProductResponse product = products.setVisible(id, true);
        audit.log(user.id(), AdminAuditAction.UPDATE, TARGET, id.toString(), "공개");
        return product;
    }

    /**
     * 상품 숨김 후 감사 기록
     *
     * @return 숨긴 상품
     */
    @PostMapping("/{id}/hide")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse hide(@PathVariable UUID id, CurrentUser user) {
        ProductResponse product = products.setVisible(id, false);
        audit.log(user.id(), AdminAuditAction.UPDATE, TARGET, id.toString(), "숨김");
        return product;
    }

    /**
     * 상품 소프트 삭제 후 감사 기록
     *
     * @return {@code {success: true}}
     */
    @DeleteMapping("/{id}")
    public Map<String, Boolean> remove(@PathVariable UUID id, CurrentUser user) {
        products.remove(id);
        audit.log(user.id(), AdminAuditAction.DELETE, TARGET, id.toString(), null);
        return Map.of("success", true);
    }
}

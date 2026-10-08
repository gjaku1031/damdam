package io.github.gjaku1031.damdam.product.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.gjaku1031.damdam.common.error.ApiException;
import io.github.gjaku1031.damdam.common.web.Paging;
import io.github.gjaku1031.damdam.product.domain.ProductSort;
import io.github.gjaku1031.damdam.product.dto.ProductPage;
import io.github.gjaku1031.damdam.product.dto.ProductResponse;
import io.github.gjaku1031.damdam.product.service.ProductService;
import lombok.RequiredArgsConstructor;

/**
 * 고객용 상품 조회 API
 *
 * 공개 상품과 판매 중인 옵션만 노출
 */
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public final class ProductController {
    /**
     * 상품 서비스
     */
    private final ProductService products;

    /**
     * 공개 상품 목록 조회
     *
     * @param page 쪽 번호, 생략 시 1
     * @param limit 쪽 크기, 생략 시 20
     * @param search 상품명 부분 일치 검색어, 생략 시 전체
     * @param category 분류 정확히 일치, 생략 시 전체
     * @param sort 정렬({@code newest}·{@code price_asc}·{@code price_desc}·{@code name}), 생략 시 {@code newest}
     * @return 정렬 기준 순 상품 쪽
     */
    @GetMapping
    public ProductPage list(@RequestParam(required = false) String page, @RequestParam(required = false) String limit,
            @RequestParam(required = false) String search, @RequestParam(required = false) String category,
            @RequestParam(required = false) String sort) {
        return products.list(true, Paging.of(page, limit, 20), search, category, ProductSort.parse(sort));
    }

    /**
     * 공개 상품 분류 목록 조회
     *
     * @return 공개 상품에 쓰인 분류 이름 오름차순
     */
    @GetMapping("/categories")
    public List<String> categories() {
        return products.categories();
    }

    /**
     * 공개 상품 상세 조회
     *
     * @return 판매 중인 옵션만 담은 상품
     * @throws ApiException 상품이 없거나 비공개·삭제일 때(404)
     */
    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable UUID id) {
        return products.get(id, true);
    }
}

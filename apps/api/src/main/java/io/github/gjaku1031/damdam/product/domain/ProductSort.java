package io.github.gjaku1031.damdam.product.domain;

import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;

import io.github.gjaku1031.damdam.common.error.ApiException;

/**
 * 상품 목록 정렬 기준
 *
 * 쿼리 문자열 값은 소문자({@code newest}, {@code price_asc} 등)
 */
public enum ProductSort {
    /**
     * 최근 등록 순
     */
    NEWEST,

    /**
     * 기본 옵션 가격 낮은 순
     */
    PRICE_ASC,

    /**
     * 기본 옵션 가격 높은 순
     */
    PRICE_DESC,

    /**
     * 상품명 가나다순
     */
    NAME;

    /**
     * 쿼리 문자열 값 해석
     *
     * @param raw 정렬 값, 비어 있으면 {@link #NEWEST}
     * @return 정렬 기준
     * @throws ApiException 알 수 없는 값일 때(400, {@code fields.sort})
     */
    public static ProductSort parse(String raw) {
        if (raw == null || raw.isBlank()) return NEWEST;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "입력 내용을 확인해주세요.",
                    Map.of("sort", "sort must be one of newest, price_asc, price_desc, name"));
        }
    }
}

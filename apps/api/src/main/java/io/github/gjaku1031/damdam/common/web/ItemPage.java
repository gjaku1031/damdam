package io.github.gjaku1031.damdam.common.web;

import java.util.List;

/**
 * {@code {items, total, page, size}} 형식의 목록 응답
 *
 * @param items 현재 쪽 항목
 * @param total 조건에 맞는 전체 항목 수
 * @param page 쪽 번호, 1부터 시작
 * @param size 쪽 크기
 * @param <T> 항목 타입
 */
public record ItemPage<T>(List<T> items, long total, int page, int size) {
    /**
     * 보정된 페이지 값으로 목록 응답 생성
     *
     * @param items 현재 쪽 항목
     * @param total 전체 항목 수
     * @param paging 보정된 쪽 번호·크기
     * @param <T> 항목 타입
     * @return 목록 응답
     */
    public static <T> ItemPage<T> of(List<T> items, long total, Paging paging) {
        return new ItemPage<>(items, total, paging.page(), paging.size());
    }
}

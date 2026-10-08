package io.github.gjaku1031.damdam.common.web;

/**
 * 쿼리 문자열의 페이지 값을 관대하게 해석한 쪽 번호·크기
 *
 * 숫자가 아니거나 0이면 기본값, 범위를 벗어나면 쪽 번호는 1 이상, 쪽 크기는 1~100으로 보정
 *
 * @param page 쪽 번호, 1부터 시작
 * @param size 쪽 크기, 1~{@value #MAX_SIZE}
 */
public record Paging(int page, int size) {
    /**
     * 쪽 크기 상한
     */
    private static final int MAX_SIZE = 100;

    /**
     * 쿼리 문자열 값 해석
     *
     * @param page 쪽 번호 문자열, 생략·빈 값이면 1
     * @param size 쪽 크기 문자열, 생략·빈 값이면 기본값
     * @param defaultSize 기본 쪽 크기
     * @return 보정된 페이지 값
     */
    public static Paging of(String page, String size, int defaultSize) {
        int parsedPage = parse(page, 1);
        int parsedSize = parse(size, defaultSize);
        return new Paging(Math.max(1, parsedPage), Math.min(MAX_SIZE, Math.max(1, parsedSize)));
    }

    /**
     * 조회 시작 위치
     *
     * @return 건너뛸 행 수
     */
    public int offset() {
        return (page - 1) * size;
    }

    /**
     * 정수 해석, 소수는 내림
     *
     * @param raw 원본 문자열
     * @param fallback 생략·빈 값·숫자 아님·0일 때 쓸 값
     * @return 해석한 값 또는 대체값
     */
    private static int parse(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            int value = (int) Math.floor(Double.parseDouble(raw.trim()));
            return value == 0 ? fallback : value;
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }
}

package io.github.gjaku1031.damdam.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 쿼리 문자열의 페이지 값 해석
 */
final class PagingTest {
    /**
     * 생략·빈 값·숫자가 아닌 값·0은 기본값
     */
    @Test
    void fallsBackToDefaultsForMissingOrInvalidValues() {
        assertThat(Paging.of(null, null, 20)).isEqualTo(new Paging(1, 20));
        assertThat(Paging.of("", " ", 20)).isEqualTo(new Paging(1, 20));
        assertThat(Paging.of("abc", "xyz", 20)).isEqualTo(new Paging(1, 20));
        assertThat(Paging.of("0", "0", 20)).isEqualTo(new Paging(1, 20));
    }

    /**
     * 범위를 벗어난 값은 쪽 번호 1 이상, 쪽 크기 1~100으로 보정
     */
    @Test
    void clampsOutOfRangeValues() {
        assertThat(Paging.of("-3", "-5", 20)).isEqualTo(new Paging(1, 1));
        assertThat(Paging.of("2", "1000", 20)).isEqualTo(new Paging(2, 100));
        assertThat(Paging.of("99999999999", "50", 20).page()).isEqualTo(1_000_000);
    }

    /**
     * 소수는 내림하고 건너뛸 행 수는 (쪽 번호 - 1) × 쪽 크기
     */
    @Test
    void floorsDecimalsAndComputesOffset() {
        Paging paging = Paging.of("3.9", "10.2", 20);

        assertThat(paging).isEqualTo(new Paging(3, 10));
        assertThat(paging.offset()).isEqualTo(20);
        assertThat(Paging.of("1000000", "100", 20).offset()).isEqualTo(99_999_900);
    }
}

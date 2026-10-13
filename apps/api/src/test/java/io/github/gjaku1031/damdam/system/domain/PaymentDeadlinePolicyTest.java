package io.github.gjaku1031.damdam.system.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * 무통장 입금 기한 계산과 기한 일수 설정 해석
 */
final class PaymentDeadlinePolicyTest {
    /**
     * 기한은 주문일(한국 시간)에서 N일 뒤의 23:59:59
     */
    @Test
    void dueAtIsEndOfDayAfterGivenDaysInSeoul() {
        // 한국 시간 10월 11일 02:20
        Instant orderedAt = Instant.parse("2026-10-10T17:20:00Z");

        // 한국 시간 10월 13일 23:59:59
        assertThat(PaymentDeadlinePolicy.dueAt(orderedAt, 2)).isEqualTo(Instant.parse("2026-10-13T14:59:59Z"));
    }

    /**
     * UTC로는 전날이어도 한국 시간으로 날짜가 바뀌었으면 바뀐 날짜부터 셈
     */
    @Test
    void dueAtCountsFromSeoulDateNotUtcDate() {
        // 한국 시간 10월 11일 23:59, 10월 12일 00:00 (UTC로는 둘 다 10월 11일)
        Instant beforeMidnight = Instant.parse("2026-10-11T14:59:00Z");
        Instant afterMidnight = Instant.parse("2026-10-11T15:00:00Z");

        assertThat(PaymentDeadlinePolicy.dueAt(beforeMidnight, 1)).isEqualTo(Instant.parse("2026-10-12T14:59:59Z"));
        assertThat(PaymentDeadlinePolicy.dueAt(afterMidnight, 1)).isEqualTo(Instant.parse("2026-10-13T14:59:59Z"));
    }

    /**
     * 기한 일수 설정은 1~30의 정수만 받고 앞뒤 공백은 무시
     */
    @Test
    void parseDaysAcceptsOnlyWholeDaysInRange() {
        assertThat(PaymentDeadlinePolicy.parseDays("1")).hasValue(1);
        assertThat(PaymentDeadlinePolicy.parseDays(" 30 ")).hasValue(30);
        assertThat(PaymentDeadlinePolicy.parseDays("0")).isEmpty();
        assertThat(PaymentDeadlinePolicy.parseDays("31")).isEmpty();
        assertThat(PaymentDeadlinePolicy.parseDays("-1")).isEmpty();
        assertThat(PaymentDeadlinePolicy.parseDays("2.5")).isEmpty();
        assertThat(PaymentDeadlinePolicy.parseDays("abc")).isEmpty();
        assertThat(PaymentDeadlinePolicy.parseDays("")).isEmpty();
    }

    /**
     * 설정이 없거나 잘못되면 기본 2일
     */
    @Test
    void daysOrDefaultFallsBackToTwoDays() {
        assertThat(PaymentDeadlinePolicy.daysOrDefault(null)).isEqualTo(2);
        assertThat(PaymentDeadlinePolicy.daysOrDefault("")).isEqualTo(2);
        assertThat(PaymentDeadlinePolicy.daysOrDefault("99")).isEqualTo(2);
        assertThat(PaymentDeadlinePolicy.daysOrDefault("5")).isEqualTo(5);
    }
}

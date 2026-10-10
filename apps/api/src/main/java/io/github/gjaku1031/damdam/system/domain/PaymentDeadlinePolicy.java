package io.github.gjaku1031.damdam.system.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.OptionalInt;

/**
 * 무통장 입금 기한 규칙
 *
 * 기한은 주문일(한국 시간) + N일의 23:59:59, 주문할 때 계산해 주문에 저장
 * 운영 설정의 N을 바꿔도 이미 저장된 주문의 기한은 바뀌지 않음
 */
public final class PaymentDeadlinePolicy {
    /**
     * 기한 날짜를 정하는 시간대
     */
    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /**
     * 설정이 없거나 잘못됐을 때의 기한 일수
     */
    public static final int DEFAULT_DAYS = 2;

    /**
     * 설정할 수 있는 최소 기한 일수
     */
    public static final int MIN_DAYS = 1;

    /**
     * 설정·연장할 수 있는 최대 기한 일수
     */
    public static final int MAX_DAYS = 30;

    /**
     * 기한 당일의 마감 시각
     */
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    /**
     * 인스턴스 생성 방지
     */
    private PaymentDeadlinePolicy() {}

    /**
     * 설정 값의 기한 일수 해석
     *
     * @param raw 운영 설정 값, 앞뒤 공백 무시
     * @return 허용 범위의 일수, 숫자가 아니거나 범위를 벗어나면 빈 값
     */
    public static OptionalInt parseDays(String raw) {
        try {
            int days = Integer.parseInt(raw.trim());
            return days >= MIN_DAYS && days <= MAX_DAYS ? OptionalInt.of(days) : OptionalInt.empty();
        } catch (NumberFormatException notNumber) {
            return OptionalInt.empty();
        }
    }

    /**
     * 설정 값의 기한 일수, 없거나 잘못된 값이면 기본값
     *
     * @param raw 운영 설정 값, 설정이 없으면 {@code null}
     * @return 기한 일수
     */
    public static int daysOrDefault(String raw) {
        return raw == null ? DEFAULT_DAYS : parseDays(raw).orElse(DEFAULT_DAYS);
    }

    /**
     * 입금 기한 계산
     *
     * @param orderedAt 주문 시각
     * @param days 기한 일수
     * @return 주문일(한국 시간)에서 {@code days}일 뒤의 23:59:59
     */
    public static Instant dueAt(Instant orderedAt, int days) {
        return LocalDate.ofInstant(orderedAt, SEOUL).plusDays(days).atTime(END_OF_DAY).atZone(SEOUL).toInstant();
    }
}

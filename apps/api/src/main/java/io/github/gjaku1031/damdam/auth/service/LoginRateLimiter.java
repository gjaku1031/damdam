package io.github.gjaku1031.damdam.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.github.gjaku1031.damdam.common.error.ApiException;

/**
 * 메모리 고정 창 카운터 기반 로그인·가입 시도 제한
 *
 * 단일 API 인스턴스 기준, 재시작하면 카운터 초기화
 *
 * <ul>
 *   <li>가입: IP당 1시간 10회</li>
 *   <li>로그인: IP당 15분 100회, IP·아이디 조합당 15분 20회</li>
 * </ul>
 */
@Component
public final class LoginRateLimiter {
    /**
     * 제한 대상 요청 종류
     */
    public enum Kind {
        /**
         * 로그인
         */
        LOGIN,

        /**
         * 가입
         */
        REGISTER
    }

    /**
     * 카운터 하나의 제한 규칙
     *
     * @param scope 카운터 키 구분값(IP 또는 IP·아이디)
     * @param maximum 창 안에서 허용하는 최대 횟수
     * @param window 고정 창 길이
     */
    private record Rule(String scope, int maximum, Duration window) {}

    /**
     * 카운터 하나의 현재 창
     *
     * @param expiresAt 창이 끝나는 시각, 첫 증가 때 고정
     * @param count 창 안의 시도 횟수
     */
    private record Window(Instant expiresAt, int count) {}

    /**
     * 카운터 키별 현재 창
     */
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * 창 시작·만료 판단 기준 시계
     */
    private final Clock clock = Clock.systemUTC();

    /**
     * 시도 횟수 기록 후 제한 초과 검사
     *
     * 규칙 순서대로 카운터 증가, 첫 초과 규칙에서 중단(이후 규칙의 카운터는 증가하지 않음)
     *
     * @param kind 요청 종류
     * @param ip 클라이언트 IP
     * @param username 정규화된 아이디, 로그인 규칙에서만 사용
     * @throws ApiException 제한을 넘었을 때(429)
     */
    public void check(Kind kind, String ip, String username) {
        // 요청 종류별 규칙 구성
        List<Rule> rules = kind == Kind.REGISTER
                ? List.of(new Rule("ip:" + ip, 10, Duration.ofHours(1)))
                : List.of(new Rule("ip:" + ip, 100, Duration.ofMinutes(15)),
                        new Rule("account:" + ip + ":" + username, 20, Duration.ofMinutes(15)));
        // 규칙마다 원자적으로 증가시키고 최대 횟수 비교, 만료된 창은 새로 시작
        Instant now = clock.instant();
        for (Rule rule : rules) {
            String key = kind.name().toLowerCase() + ":" + rule.scope();
            Window window = windows.compute(key, (ignored, current) -> current == null
                    || !current.expiresAt().isAfter(now) ? new Window(now.plus(rule.window()), 1)
                            : new Window(current.expiresAt(), current.count() + 1));
            if (window.count() > rule.maximum()) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "요청이 많습니다. 잠시 후 다시 시도해주세요.");
            }
        }
    }

    /**
     * 만료된 창 정리
     *
     * 10분마다 실행, 더 이상 쓰이지 않는 IP·아이디 카운터의 메모리 회수
     */
    @Scheduled(fixedDelay = 600_000)
    void purgeExpired() {
        Instant now = clock.instant();
        windows.values().removeIf(window -> !window.expiresAt().isAfter(now));
    }
}

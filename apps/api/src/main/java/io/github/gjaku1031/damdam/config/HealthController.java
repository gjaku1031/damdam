package io.github.gjaku1031.damdam.config;

import java.util.Map;

import org.jooq.DSLContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.gjaku1031.damdam.common.error.ApiException;
import lombok.RequiredArgsConstructor;

/**
 * 컨테이너 상태 점검
 *
 * PostgreSQL이 응답해야 정상
 */
@RestController
@RequiredArgsConstructor
public final class HealthController {
    /**
     * PostgreSQL 연결 확인용 jOOQ 문맥
     */
    private final DSLContext dsl;

    /**
     * DB 응답 확인
     *
     * @return {@code {"status": "ok"}}
     * @throws ApiException DB가 응답하지 않을 때(503)
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        try {
            dsl.selectOne().fetch();
            return Map.of("status", "ok");
        } catch (RuntimeException unavailable) {
            throw ApiException.serviceUnavailable("Database is unavailable.");
        }
    }
}

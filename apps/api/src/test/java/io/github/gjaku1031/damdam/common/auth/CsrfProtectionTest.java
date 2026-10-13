package io.github.gjaku1031.damdam.common.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import io.github.gjaku1031.damdam.fixture.ApiClient;
import io.github.gjaku1031.damdam.fixture.ApiResponse;
import io.github.gjaku1031.damdam.fixture.IntegrationTest;

/**
 * 상태를 바꾸는 요청의 CSRF 토큰 검사
 */
final class CsrfProtectionTest extends IntegrationTest {
    /**
     * 토큰 없이 보낸 상태 변경 요청은 로그인한 세션이어도 403
     */
    @Test
    void rejectsUnsafeRequestWithoutToken() {
        ApiClient member = member();

        ApiResponse response = member.send("POST", "/auth/logout", null, Map.of());

        assertThat(response.status()).isEqualTo(403);
        assertThat(ok(member.get("/auth/session")).body().path("user").isNull()).as("로그아웃되지 않음").isFalse();
    }

    /**
     * 다른 세션에서 받은 토큰은 403
     */
    @Test
    void rejectsTokenOfAnotherSession() {
        ApiClient member = member();
        String foreign = visitor().csrfToken();

        ApiResponse response = member.send("POST", "/auth/logout", null, Map.of("X-CSRF-Token", foreign));

        assertThat(response.status()).isEqualTo(403);
    }

    /**
     * 자기 세션의 토큰을 실으면 통과
     */
    @Test
    void acceptsTokenOfSameSession() {
        ApiClient member = member();

        ok(member.post("/auth/logout", null));

        assertThat(ok(member.get("/auth/session")).body().path("user").isNull()).isTrue();
    }

    /**
     * 같은 세션에서는 토큰이 바뀌지 않고, 조회 요청은 토큰 없이 통과
     */
    @Test
    void keepsTokenWithinSessionAndAllowsReads() {
        ApiClient visitor = visitor();

        String first = visitor.csrfToken();
        String second = visitor.csrfToken();

        assertThat(first).hasSizeGreaterThanOrEqualTo(43).isEqualTo(second);
        assertThat(visitor.get("/products").status()).isEqualTo(200);
    }

    /**
     * 로그인 요청도 토큰이 없으면 403, 토큰이 있으면 로그인됨
     */
    @Test
    void requiresTokenForLogin() {
        ApiClient visitor = visitor();
        Map<String, String> credentials = Map.of("username", ADMIN_USERNAME, "password", ADMIN_PASSWORD);

        assertThat(visitor.send("POST", "/auth/login", credentials, Map.of()).status()).isEqualTo(403);
        assertThat(visitor.post("/auth/login", credentials).status()).isEqualTo(200);
    }

    /**
     * 로그인하면 세션이 새로 발급되므로 로그인 전에 받은 토큰은 403, 새 토큰은 통과
     */
    @Test
    void invalidatesTokenWhenSessionChangesOnLogin() {
        ApiClient visitor = visitor();
        String beforeLogin = visitor.csrfToken();
        ok(visitor.login(ADMIN_USERNAME, ADMIN_PASSWORD));

        ApiResponse stale = visitor.send("POST", "/auth/logout", null, Map.of("X-CSRF-Token", beforeLogin));

        assertThat(stale.status()).isEqualTo(403);
        assertThat(visitor.csrfToken()).isNotEqualTo(beforeLogin);
        ok(visitor.post("/auth/logout", null));
    }

    /**
     * 로그인 직후 여러 요청이 동시에 토큰을 받아도 모두 같은 값(로그인할 때 세션과 함께 발급)
     */
    @Test
    void concurrentTokenRequestsAfterLoginAgree() throws Exception {
        ApiClient member = member();
        List<Callable<String>> requests = new ArrayList<>();
        for (int index = 0; index < 12; index++) requests.add(member::csrfToken);

        Set<String> tokens = new HashSet<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(requests.size())) {
            for (Future<String> token : executor.invokeAll(requests)) tokens.add(token.get());
        }

        assertThat(tokens).hasSize(1);
    }

    /**
     * 허용하지 않은 출처의 요청은 토큰이 맞아도 403
     */
    @Test
    void rejectsForeignOriginEvenWithValidToken() {
        ApiClient member = member();

        ApiResponse response = member.send("POST", "/auth/logout", null,
                Map.of("X-CSRF-Token", member.csrfToken(), "Origin", "https://evil.example"));

        assertThat(response.status()).isEqualTo(403);
        assertThat(ok(member.get("/auth/session")).body().path("user").isNull()).isFalse();
    }

    /**
     * 토큰 검사가 권한 검사보다 먼저라, 비로그인 운영 요청은 토큰이 없으면 403이고 있으면 401
     */
    @Test
    void checksTokenBeforeAuthentication() {
        ApiClient visitor = visitor();

        assertThat(visitor.send("POST", "/admin/products", Map.of(), Map.of()).status()).isEqualTo(403);
        assertThat(visitor.post("/admin/products", Map.of()).status()).isEqualTo(401);
    }

    /**
     * 없는 경로는 토큰 검사 없이 원래대로 404
     */
    @Test
    void leavesUnknownPathsAsNotFound() {
        assertThat(visitor().send("POST", "/no-such-endpoint", null, Map.of()).status()).isEqualTo(404);
    }
}

package io.github.gjaku1031.damdam.common.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.gjaku1031.damdam.fixture.ApiClient;
import io.github.gjaku1031.damdam.fixture.IntegrationTest;

/**
 * 로그인·운영자 권한 검사와 응답의 기본 캐시 정책
 */
final class AccessControlTest extends IntegrationTest {
    /**
     * 운영 API는 비로그인 401, 고객 403, 운영자만 200
     */
    @Test
    void adminApiIsForOperatorsOnly() {
        assertThat(visitor().get("/admin/orders").status()).isEqualTo(401);
        assertThat(member().get("/admin/orders").status()).isEqualTo(403);
        assertThat(admin().get("/admin/orders").status()).isEqualTo(200);
    }

    /**
     * 대소문자·경로 매개변수·퍼센트 인코딩으로 경로를 바꿔 써도 운영 권한 검사를 피할 수 없음
     */
    @Test
    void adminCheckCannotBeBypassedByPathSpelling() {
        ApiClient member = member();

        for (String path : new String[] {"/ADMIN/orders", "/Admin/dashboard", "/admin;x=1/orders", "/%61dmin/orders",
                "/admin/orders;probe.jpg"}) {
            assertThat(member.get(path).status()).as(path).isEqualTo(403);
            assertThat(visitor().get(path).status()).as(path).isEqualTo(401);
        }
    }

    /**
     * 로그인이 필요한 고객 API는 비로그인 401
     */
    @Test
    void memberApiRequiresLogin() {
        assertThat(visitor().get("/orders/me").status()).isEqualTo(401);
        assertThat(visitor().get("/cart/items").status()).isEqualTo(401);
        assertThat(member().get("/orders/me").status()).isEqualTo(200);
    }

    /**
     * 탈퇴한 계정의 다른 세션은 다음 요청부터 401
     */
    @Test
    void sessionsOfWithdrawnAccountStopWorking() {
        String username = uniqueName();
        ApiClient first = member(username);
        ApiClient second = visitor();
        ok(second.login(username, MEMBER_PASSWORD));

        ok(first.post("/auth/withdraw", Map.of("password", MEMBER_PASSWORD)));

        assertThat(second.get("/orders/me").status()).isEqualTo(401);
    }

    /**
     * JSON 응답과 오류 응답은 기본이 저장 금지라 CDN이 개인 정보나 오류를 캐시하지 못함
     */
    @Test
    void responsesAreNoStoreByDefault() {
        assertThat(visitor().get("/products").header("Cache-Control")).isEqualTo("no-store");
        assertThat(visitor().get("/orders/me").header("Cache-Control")).isEqualTo("no-store");
        assertThat(visitor().get("/no-such-endpoint;probe.jpg").header("Cache-Control")).isEqualTo("no-store");
        assertThat(member().get("/orders/me").header("Cache-Control")).isEqualTo("no-store");
    }
}

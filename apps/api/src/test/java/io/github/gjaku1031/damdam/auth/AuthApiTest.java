package io.github.gjaku1031.damdam.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.gjaku1031.damdam.fixture.ApiClient;
import io.github.gjaku1031.damdam.fixture.ApiResponse;
import io.github.gjaku1031.damdam.fixture.IntegrationTest;
import tools.jackson.databind.JsonNode;

/**
 * 가입·로그인·로그아웃·비밀번호 변경·탈퇴
 */
final class AuthApiTest extends IntegrationTest {
    /**
     * 가입하면 고객 권한으로 바로 로그인되고 비밀번호는 Argon2id 해시로만 저장
     */
    @Test
    void registrationSignsInAsCustomerAndStoresOnlyHash() {
        String username = uniqueName();

        ApiClient member = member(username);

        JsonNode user = ok(member.get("/auth/session")).body().path("user");
        assertThat(user.path("username").asString()).isEqualTo(username);
        assertThat(user.path("role").asString()).isEqualTo("CUSTOMER");
        String stored = jdbc.queryForObject("select \"passwordHash\" from \"users\" where \"username\" = ?", String.class,
                username);
        assertThat(stored).startsWith("$argon2id$").doesNotContain(MEMBER_PASSWORD);
    }

    /**
     * 이미 있는 아이디는 대소문자를 바꿔도 409
     */
    @Test
    void rejectsDuplicateUsernameIgnoringCase() {
        String username = uniqueName();
        member(username);

        ApiResponse response = visitor().post("/auth/register", registration(username.toUpperCase(), MEMBER_PASSWORD, true));

        assertThat(response.status()).isEqualTo(409);
    }

    /**
     * 형식에 맞지 않는 아이디·비밀번호와 약관 미동의는 400이고 어느 항목인지 알려줌
     */
    @Test
    void rejectsInvalidRegistrationWithFieldMessages() {
        ApiResponse shortName = visitor().post("/auth/register", registration("abc", MEMBER_PASSWORD, true));
        ApiResponse weakPassword = visitor().post("/auth/register", registration(uniqueName(), "abcdefgh", true));
        ApiResponse noTerms = visitor().post("/auth/register", registration(uniqueName(), MEMBER_PASSWORD, false));

        assertThat(shortName.status()).isEqualTo(400);
        assertThat(shortName.body().path("fields").has("username")).isTrue();
        assertThat(weakPassword.status()).isEqualTo(400);
        assertThat(weakPassword.body().path("fields").has("password")).isTrue();
        assertThat(noTerms.status()).isEqualTo(400);
    }

    /**
     * 틀린 비밀번호와 없는 아이디는 같은 401 응답이라 아이디 존재 여부가 드러나지 않음
     */
    @Test
    void wrongPasswordAndUnknownUserLookTheSame() {
        String username = uniqueName();
        member(username);

        ApiResponse wrongPassword = visitor().login(username, "wrong-pass-1");
        ApiResponse unknownUser = visitor().login(uniqueName(), MEMBER_PASSWORD);

        assertThat(wrongPassword.status()).isEqualTo(401);
        assertThat(unknownUser.status()).isEqualTo(401);
        assertThat(wrongPassword.message()).isEqualTo(unknownUser.message());
    }

    /**
     * 로그인은 아이디 대소문자를 구분하지 않고, 로그아웃하면 세션 사용자가 사라짐
     */
    @Test
    void loginIgnoresUsernameCaseAndLogoutEndsSession() {
        String username = uniqueName();
        member(username);
        ApiClient browser = visitor();

        ok(browser.login(username.toUpperCase(), MEMBER_PASSWORD));
        assertThat(ok(browser.get("/auth/me")).body().path("username").asString()).isEqualTo(username);
        ok(browser.post("/auth/logout", null));

        assertThat(ok(browser.get("/auth/session")).body().path("user").isNull()).isTrue();
        assertThat(browser.get("/auth/me").status()).isEqualTo(401);
    }

    /**
     * 비밀번호 변경은 현재 비밀번호가 맞아야 하고, 바꾼 뒤에는 새 비밀번호로만 로그인됨
     */
    @Test
    void changesPasswordOnlyWithCurrentPassword() {
        String username = uniqueName();
        ApiClient member = member(username);

        ApiResponse wrong = member.post("/auth/password", Map.of("currentPassword", "wrong-pass-1", "newPassword", "next-pass-2"));
        ApiResponse changed = member.post("/auth/password",
                Map.of("currentPassword", MEMBER_PASSWORD, "newPassword", "next-pass-2"));

        assertThat(wrong.status()).isEqualTo(400);
        assertThat(wrong.body().path("fields").has("currentPassword")).isTrue();
        assertThat(changed.status()).isEqualTo(200);
        assertThat(visitor().login(username, MEMBER_PASSWORD).status()).isEqualTo(401);
        assertThat(visitor().login(username, "next-pass-2").status()).isEqualTo(200);
    }

    /**
     * 탈퇴하면 세션이 끝나고 다시 로그인할 수 없으며, 같은 아이디로 다시 가입할 수도 없음
     */
    @Test
    void withdrawalEndsSessionAndKeepsUsernameTaken() {
        String username = uniqueName();
        ApiClient member = member(username);

        assertThat(member.post("/auth/withdraw", Map.of("password", "wrong-pass-1")).status()).isEqualTo(400);
        ok(member.post("/auth/withdraw", Map.of("password", MEMBER_PASSWORD)));

        assertThat(ok(member.get("/auth/session")).body().path("user").isNull()).isTrue();
        assertThat(visitor().login(username, MEMBER_PASSWORD).status()).isEqualTo(401);
        assertThat(visitor().post("/auth/register", registration(username, MEMBER_PASSWORD, true)).status()).isEqualTo(409);
    }

    /**
     * 입금 전이거나 제작·발송 중인 주문이 있으면 탈퇴할 수 없음
     */
    @Test
    void cannotWithdrawWithOrderInProgress() {
        ApiClient member = member();
        created(member.post("/orders", orderOf(variantId(product(admin(), 3)), 1)));

        ApiResponse response = member.post("/auth/withdraw", Map.of("password", MEMBER_PASSWORD));

        assertThat(response.status()).isEqualTo(409);
        assertThat(ok(member.get("/auth/session")).body().path("user").isNull()).isFalse();
    }

    /**
     * 운영자 계정은 탈퇴할 수 없음
     */
    @Test
    void operatorCannotWithdraw() {
        assertThat(admin().post("/auth/withdraw", Map.of("password", ADMIN_PASSWORD)).status()).isEqualTo(403);
    }

    /**
     * 가입 요청 본문
     *
     * @param termsAccepted 필수 약관 두 개의 동의 여부
     */
    private static Map<String, Object> registration(String username, String password, boolean termsAccepted) {
        return Map.of("username", username, "password", password, "serviceTermsAccepted", termsAccepted,
                "privacyTermsAccepted", termsAccepted);
    }
}

package io.github.gjaku1031.damdam.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import io.github.gjaku1031.damdam.common.error.ApiException;

/**
 * 로그인·가입 시도 제한
 */
final class LoginRateLimiterTest {
    /**
     * 검증 대상, 테스트마다 빈 횟수로 시작
     */
    private final LoginRateLimiter limiter = new LoginRateLimiter();

    /**
     * 같은 IP·아이디의 로그인은 15분에 20번까지, 21번째는 429
     */
    @Test
    void limitsLoginAttemptsPerAccount() {
        for (int attempt = 0; attempt < 20; attempt++) {
            limiter.check(LoginRateLimiter.Kind.LOGIN, "203.0.113.1", "member");
        }

        assertThatThrownBy(() -> limiter.check(LoginRateLimiter.Kind.LOGIN, "203.0.113.1", "member"))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    /**
     * 아이디별 제한은 다른 아이디와 다른 IP에 영향을 주지 않음
     */
    @Test
    void accountLimitDoesNotAffectOtherAccountsOrAddresses() {
        for (int attempt = 0; attempt < 20; attempt++) {
            limiter.check(LoginRateLimiter.Kind.LOGIN, "203.0.113.2", "member");
        }

        assertThatCode(() -> limiter.check(LoginRateLimiter.Kind.LOGIN, "203.0.113.2", "other")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.check(LoginRateLimiter.Kind.LOGIN, "203.0.113.3", "member")).doesNotThrowAnyException();
    }

    /**
     * 아이디를 바꿔 가며 시도해도 같은 IP의 로그인은 15분에 100번까지
     */
    @Test
    void limitsLoginAttemptsPerAddressAcrossAccounts() {
        for (int attempt = 0; attempt < 100; attempt++) {
            limiter.check(LoginRateLimiter.Kind.LOGIN, "203.0.113.4", "member" + attempt);
        }

        assertThatThrownBy(() -> limiter.check(LoginRateLimiter.Kind.LOGIN, "203.0.113.4", "another"))
                .isInstanceOf(ApiException.class);
    }

    /**
     * 가입은 같은 IP에서 1시간에 10번까지이고 로그인 횟수와 따로 셈
     */
    @Test
    void limitsRegistrationsPerAddressSeparatelyFromLogins() {
        for (int attempt = 0; attempt < 10; attempt++) {
            limiter.check(LoginRateLimiter.Kind.REGISTER, "203.0.113.5", "member" + attempt);
        }

        assertThatThrownBy(() -> limiter.check(LoginRateLimiter.Kind.REGISTER, "203.0.113.5", "another"))
                .isInstanceOf(ApiException.class);
        assertThatCode(() -> limiter.check(LoginRateLimiter.Kind.LOGIN, "203.0.113.5", "member")).doesNotThrowAnyException();
    }
}

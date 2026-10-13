package io.github.gjaku1031.damdam.common.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

/**
 * 세션에 묶인 CSRF 토큰의 발급과 비교
 */
final class CsrfTokensTest {
    /**
     * 세션이 없으면 세션을 만들어 256비트 토큰을 저장하고, 같은 세션에서는 같은 값을 돌려줌
     */
    @Test
    void issuesOneTokenPerSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        String token = CsrfTokens.issue(request);

        assertThat(request.getSession(false)).isNotNull();
        assertThat(token).matches("[A-Za-z0-9_-]{43}");
        assertThat(CsrfTokens.issue(request)).isEqualTo(token);
    }

    /**
     * 세션이 다르면 토큰도 다름
     */
    @Test
    void issuesDifferentTokensForDifferentSessions() {
        assertThat(CsrfTokens.issue(new MockHttpServletRequest())).isNotEqualTo(CsrfTokens.issue(new MockHttpServletRequest()));
    }

    /**
     * 헤더의 값이 세션의 토큰과 같을 때만 일치
     */
    @Test
    void matchesOnlyTheSessionToken() {
        MockHttpSession session = new MockHttpSession();
        String token = CsrfTokens.issue(requestWith(session, null));

        assertThat(CsrfTokens.matches(requestWith(session, token))).isTrue();
        assertThat(CsrfTokens.matches(requestWith(session, token + "x"))).isFalse();
        assertThat(CsrfTokens.matches(requestWith(session, ""))).isFalse();
        assertThat(CsrfTokens.matches(requestWith(session, null))).isFalse();
    }

    /**
     * 세션이 없거나 세션에 토큰이 없으면 헤더에 무엇이 있어도 불일치, 검사 때문에 세션을 만들지도 않음
     */
    @Test
    void neverMatchesWithoutSessionToken() {
        MockHttpServletRequest withoutSession = new MockHttpServletRequest();
        withoutSession.addHeader(CsrfTokens.HEADER, "anything");

        assertThat(CsrfTokens.matches(withoutSession)).isFalse();
        assertThat(withoutSession.getSession(false)).isNull();
        assertThat(CsrfTokens.matches(requestWith(new MockHttpSession(), "anything"))).isFalse();
    }

    /**
     * 세션과 토큰 헤더를 가진 요청 생성
     *
     * @param token 헤더 값, {@code null}이면 헤더 없음
     */
    private static MockHttpServletRequest requestWith(MockHttpSession session, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        if (token != null) request.addHeader(CsrfTokens.HEADER, token);
        return request;
    }
}

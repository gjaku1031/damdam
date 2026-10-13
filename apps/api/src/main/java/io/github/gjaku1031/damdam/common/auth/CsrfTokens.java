package io.github.gjaku1031.damdam.common.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 세션에 묶인 CSRF 토큰 발급과 확인
 *
 * 다른 사이트가 사용자의 브라우저로 요청을 보내면 쿠키는 따라가지만, 세션에 저장된 토큰 값은 그 사이트가 읽을 수 없음
 * 그래서 상태를 바꾸는 요청은 쿠키와 별도로 이 토큰을 {@link #HEADER} 헤더에 실어야 통과
 * 토큰은 세션마다 하나이며 세션이 살아 있는 동안 바뀌지 않음, 로그인·로그아웃으로 세션이 바뀌면 새로 발급받아야 함
 */
public final class CsrfTokens {
    /**
     * 토큰을 싣는 요청 헤더 이름
     */
    public static final String HEADER = "X-CSRF-Token";

    /**
     * 토큰을 저장하는 세션 속성 이름
     */
    private static final String SESSION_ATTRIBUTE = "csrfToken";

    /**
     * 토큰 바이트 수(256비트)
     */
    private static final int TOKEN_BYTES = 32;

    /**
     * 토큰 생성기
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 인스턴스 생성 방지
     */
    private CsrfTokens() {}

    /**
     * 현재 세션의 토큰 조회, 없으면 발급
     *
     * 로그인 전에도 로그인·가입 요청에 토큰이 필요하므로 세션이 없으면 새로 만듦
     * 세션 저장소에는 "없을 때만 저장"이 없어, 토큰 없는 세션에 요청이 동시에 오면 요청마다 다른 값을 만들 수 있음
     * 그래서 로그인 세션은 만들 때 바로 발급해 두고, 로그인 전 세션은 클라이언트가 요청을 하나로 모아 보냄
     *
     * @return URL에 안전한 Base64 문자열 토큰
     */
    public static String issue(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        if (session.getAttribute(SESSION_ATTRIBUTE) instanceof String existing) return existing;
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(SESSION_ATTRIBUTE, token);
        return token;
    }

    /**
     * 요청 헤더의 토큰이 세션의 토큰과 같은지 확인
     *
     * 비교 시간으로 토큰을 추측할 수 없도록 길이와 무관하게 일정한 시간에 비교
     *
     * @return 세션과 헤더에 토큰이 모두 있고 값이 같으면 {@code true}
     */
    public static boolean matches(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        String header = request.getHeader(HEADER);
        if (session == null || header == null || !(session.getAttribute(SESSION_ATTRIBUTE) instanceof String expected)) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), header.getBytes(StandardCharsets.UTF_8));
    }
}

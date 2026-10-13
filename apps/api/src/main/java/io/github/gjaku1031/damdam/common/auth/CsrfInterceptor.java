package io.github.gjaku1031.damdam.common.auth;

import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import io.github.gjaku1031.damdam.common.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 상태를 바꾸는 요청의 CSRF 토큰 검사
 *
 * 조회 전용 메서드({@code GET}·{@code HEAD}·{@code OPTIONS}·{@code TRACE})가 아닌 모든 컨트롤러 요청에 적용
 * 로그인·가입처럼 로그인 전 요청도 예외 없이 검사(다른 사이트가 피해자를 공격자 계정으로 로그인시키는 것을 막음)
 * 출처 검사(CORS 허용 출처)와 세션 쿠키의 {@code SameSite=Lax}에 더하는 방어선
 */
@Component
public final class CsrfInterceptor implements HandlerInterceptor {
    /**
     * 토큰 없이 허용하는 조회 전용 메서드
     */
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    /**
     * 핸들러 실행 전 토큰 검사
     *
     * 컨트롤러 메서드가 아닌 요청(없는 경로 등)은 원래의 404·405 응답이 나가도록 통과
     *
     * @param request 현재 요청
     * @param response 현재 응답
     * @param handler 실행할 핸들러
     * @return 항상 {@code true}
     * @throws ApiException 상태를 바꾸는 요청에 토큰이 없거나 세션의 토큰과 다를 때(403)
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod) || SAFE_METHODS.contains(request.getMethod())) return true;
        if (!CsrfTokens.matches(request)) {
            throw ApiException.forbidden("보안 확인에 실패했습니다. 새로고침한 뒤 다시 시도해주세요.");
        }
        return true;
    }
}

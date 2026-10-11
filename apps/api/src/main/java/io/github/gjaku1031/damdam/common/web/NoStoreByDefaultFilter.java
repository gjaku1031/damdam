package io.github.gjaku1031.damdam.common.web;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 모든 응답의 기본 캐시 정책을 저장 금지로 지정
 *
 * 캐시 지시가 없는 응답은 CDN이 주소의 확장자만 보고 저장함
 * {@code /api/orders;x.jpg}처럼 경로 매개변수로 확장자를 붙이면 로그인한 사람의 응답도 저장돼 다른 사람에게 전달될 수 있음
 * 캐시를 허용할 응답은 핸들러가 {@code Cache-Control}을 직접 덮어씀
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class NoStoreByDefaultFilter extends OncePerRequestFilter {
    /**
     * 기본 캐시 정책
     */
    private static final String NO_STORE = "no-store";

    /**
     * 응답에 저장 금지를 먼저 적어 두고 다음 필터로 전달
     *
     * 뒤의 필터·인터셉터·오류 처리기가 만든 응답에도 같은 헤더가 남음
     *
     * @param request 현재 요청
     * @param response 현재 응답
     * @param chain 다음 필터 체인
     * @throws ServletException 다음 필터 처리 실패 시
     * @throws IOException 다음 필터 처리 실패 시
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
        chain.doFilter(request, response);
    }
}

package io.github.gjaku1031.damdam.common.web;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.gjaku1031.damdam.common.error.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON 요청 본문 1MB 제한
 *
 * 사진 업로드(multipart)는 제외, 별도 5MB 제한을 따름
 * {@link NoStoreByDefaultFilter} 바로 다음에 실행, 여기서 직접 쓰는 413 응답에도 저장 금지가 붙음
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public final class RequestBodyLimitFilter extends OncePerRequestFilter {
    /**
     * multipart가 아닌 요청 본문 최대 크기(바이트)
     */
    private static final long MAX_JSON_BYTES = 1024 * 1024;

    /**
     * 오류 응답 직렬화기
     */
    private final JsonMapper json;

    /**
     * 선언된 본문 길이가 제한을 넘으면 413 오류 응답, 아니면 다음 필터로 전달
     *
     * {@code Content-Length} 헤더 기준으로만 판단, 길이를 밝히지 않은 요청은 통과
     *
     * @param request 현재 요청
     * @param response 현재 응답
     * @param chain 다음 필터 체인
     * @throws ServletException 다음 필터 처리 실패 시
     * @throws IOException 응답 쓰기 또는 다음 필터 처리 실패 시
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String type = request.getContentType();
        boolean multipart = type != null && type.toLowerCase().startsWith("multipart/");
        // 제한 초과: 컨트롤러까지 가지 않고 공통 오류 형식으로 응답
        if (!multipart && request.getContentLengthLong() > MAX_JSON_BYTES) {
            response.setStatus(HttpStatus.CONTENT_TOO_LARGE.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            json.writeValue(response.getOutputStream(),
                    ErrorResponse.of(HttpStatus.CONTENT_TOO_LARGE.value(), "request entity too large", null));
            return;
        }
        chain.doFilter(request, response);
    }
}

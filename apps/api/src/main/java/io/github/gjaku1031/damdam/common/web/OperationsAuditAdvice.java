package io.github.gjaku1031.damdam.common.web;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import io.github.gjaku1031.damdam.common.auth.AuthInterceptor;
import io.github.gjaku1031.damdam.common.auth.CurrentUser;
import io.github.gjaku1031.damdam.system.domain.AdminAuditAction;
import io.github.gjaku1031.damdam.system.service.SystemService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * 성공한 운영 변경 요청({@code /api/admin/**}, GET 제외)의 운영 기록 저장
 *
 * 상품·게시글·대리 주문 생성·주문 입금 기한 변경은 서비스에서 더 구체적인 기록을 남기므로 제외(게시글 분류는 포함)
 */
@RestControllerAdvice
@RequiredArgsConstructor
public final class OperationsAuditAdvice implements ResponseBodyAdvice<Object> {
    /**
     * 경로 첫 구간별 기록용 대상 이름
     */
    private static final Map<String, String> TARGET_NAMES = Map.of(
            "orders", "주문", "users", "회원", "inventory", "재고",
            "system", "운영 설정", "posts", "게시글 분류");

    /**
     * 운영 기록 저장 서비스
     */
    private final SystemService system;

    /**
     * 컨트롤러 응답에만 적용
     *
     * @param returnType 핸들러 반환 타입
     * @param converterType 선택된 메시지 변환기
     * @return {@code @RestController} 응답이면 {@code true}
     */
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 예외 처리기가 만든 오류 응답은 기록하지 않음
        return returnType.getContainingClass().isAnnotationPresent(RestController.class);
    }

    /**
     * 응답 본문을 쓰기 전에 운영 기록 저장
     *
     * 1. 인증된 운영자의 GET 아닌 {@code /api/admin/**} 요청만 대상
     * 2. 자체 기록을 남기는 상품·게시글·주문 입금 기한 경로 제외(게시글 분류는 기록)
     * 3. HTTP 메서드와 마지막 경로 구간으로 작업 종류·설명 결정 후 저장
     *
     * @param body 응답 본문
     * @param returnType 핸들러 반환 타입
     * @param contentType 응답 미디어 타입
     * @param converterType 선택된 메시지 변환기
     * @param serverRequest 현재 요청
     * @param response 현재 응답
     * @return 변경하지 않은 응답 본문
     */
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
            Class<? extends HttpMessageConverter<?>> converterType, ServerHttpRequest serverRequest,
            ServerHttpResponse response) {
        // 기록 대상 요청 판별
        if (!(serverRequest instanceof ServletServerHttpRequest servlet)) return body;
        HttpServletRequest request = servlet.getServletRequest();
        CurrentUser user = AuthInterceptor.current(request);
        List<String> segments = RequestPaths.segments(request);
        String method = request.getMethod();
        if (user == null || !RequestPaths.isAdmin(segments) || "GET".equals(method)) return body;

        // 서비스가 직접 기록하는 상품·게시글·대리 주문 생성·주문 입금 기한 경로 제외
        String path = segments.stream().skip(2).map(segment -> segment.toLowerCase(Locale.ROOT))
                .collect(Collectors.joining("/"));
        if (path.matches("^products(/.*)?$") || path.matches("^posts(/.*)?$") && !path.matches("^posts/categories(/.*)?$")
                || path.equals("orders") || path.matches("^orders/[^/]+/payment-due$")) {
            return body;
        }

        // 작업 종류: DELETE는 삭제, 상태 전환이 아닌 POST는 등록, 나머지는 수정
        String[] parts = path.split("/");
        String last = parts[parts.length - 1];
        AdminAuditAction action = "DELETE".equals(method) ? AdminAuditAction.DELETE
                : "POST".equals(method) && !last.matches("hide|show|block|unblock|adjust") ? AdminAuditAction.CREATE
                : AdminAuditAction.UPDATE;
        // 대상 이름과 설명 조립 후 저장
        String name = "cms".equals(parts[0]) ? (parts.length > 1 && "faqs".equals(parts[1]) ? "FAQ" : "공지사항")
                : TARGET_NAMES.getOrDefault(parts[0], "운영 항목");
        system.log(user.id(), action, parts[0].toUpperCase(Locale.ROOT), parts.length > 1 ? parts[1] : null,
                name + " " + verb(parts[0], last, method));
        return body;
    }

    /**
     * 운영 기록 설명의 동작 문구
     *
     * @param target 경로 첫 구간(대상 종류)
     * @param last 경로 마지막 구간
     * @param method HTTP 메서드
     * @return {@code 숨김}, {@code 상태 변경}, {@code 등록}처럼 대상 이름 뒤에 붙일 문구
     */
    private static String verb(String target, String last, String method) {
        return switch (last) {
            case "hide" -> "숨김";
            case "show" -> "공개";
            case "block" -> "댓글 제한";
            case "unblock" -> "댓글 제한 해제";
            case "adjust" -> "수량 조정";
            case "status" -> "상태 변경";
            default -> "system".equals(target) ? "수정"
                    : "DELETE".equals(method) ? "삭제" : "POST".equals(method) ? "등록" : "수정";
        };
    }
}

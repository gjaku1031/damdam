package io.github.gjaku1031.damdam.common.web;

import java.util.List;

import org.springframework.http.server.PathContainer.PathSegment;
import org.springframework.web.util.ServletRequestPathUtils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 핸들러 매핑이 비교에 쓰는 것과 같은 요청 경로 구간
 *
 * 원본 URI 문자열({@code getRequestURI})은 퍼센트 인코딩과 {@code ;} 경로 매개변수를 그대로 담아
 * {@code /api/admin;x/orders}·{@code /api/%61dmin/orders}처럼 매핑은 되지만 문자열 비교는 빗나가는 요청이 생김
 * 권한·기록 판단은 반드시 이 구간으로 수행
 */
public final class RequestPaths {
    /**
     * 인스턴스 생성 방지
     */
    private RequestPaths() {}

    /**
     * 디코딩하고 경로 매개변수를 뗀 경로 구간 조회
     *
     * @param request 핸들러 매핑을 거친 요청
     * @return 구분자를 뺀 구간 목록, {@code /api/admin;x/orders}면 {@code [api, admin, orders]}
     */
    public static List<String> segments(HttpServletRequest request) {
        return ServletRequestPathUtils.getParsedRequestPath(request).pathWithinApplication().elements().stream()
                .filter(PathSegment.class::isInstance)
                .map(element -> ((PathSegment) element).valueToMatch())
                .toList();
    }

    /**
     * 운영 API 경로({@code /api/admin} 이하) 여부
     *
     * @param segments {@link #segments}로 구한 경로 구간
     * @return 첫 두 구간이 대소문자 무관하게 {@code api}·{@code admin}이면 {@code true}
     */
    public static boolean isAdmin(List<String> segments) {
        return segments.size() >= 2 && "api".equalsIgnoreCase(segments.get(0)) && "admin".equalsIgnoreCase(segments.get(1));
    }
}

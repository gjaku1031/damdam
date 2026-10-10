package io.github.gjaku1031.damdam.common.auth;

import java.lang.annotation.Annotation;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import io.github.gjaku1031.damdam.common.error.ApiException;
import io.github.gjaku1031.damdam.common.web.RequestPaths;
import io.github.gjaku1031.damdam.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 세션 사용자 확인과 운영 경로 보호
 *
 * {@code /api/admin} 이하 경로(대소문자 무관)는 컨트롤러 표시와 관계없이 운영자만 허용
 * 경로는 핸들러 매핑과 같은 기준({@link RequestPaths})으로 판단해 인코딩·경로 매개변수로 검사를 피할 수 없게 함
 * 사용자는 매 요청 DB에서 다시 읽으므로 삭제·권한 변경이 즉시 반영됨
 */
@Component
@RequiredArgsConstructor
public final class AuthInterceptor implements HandlerInterceptor {
    /**
     * 회원 저장소
     */
    private final UserRepository users;

    /**
     * 핸들러 실행 전 로그인·운영자 권한 검사
     *
     * 1. 운영 경로 또는 {@link AdminOnly}면 운영자 필요, {@link LoginRequired}면 로그인 필요, 둘 다 아니면 통과
     * 2. 세션 사용자 ID로 삭제되지 않은 계정 조회
     * 3. 권한 확인 후 {@link CurrentUser}를 요청 속성에 저장
     *
     * @param request 현재 요청
     * @param response 현재 응답
     * @param handler 실행할 핸들러
     * @return 항상 {@code true}, 거부는 예외로 처리
     * @throws ApiException 세션이 없거나 계정이 삭제됐을 때(401), 운영자가 아닐 때(403)
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 인증이 필요 없는 핸들러는 통과
        if (!(handler instanceof HandlerMethod method)) return true;
        boolean adminRequired = RequestPaths.isAdmin(RequestPaths.segments(request)) || annotated(method, AdminOnly.class);
        if (!adminRequired && !annotated(method, LoginRequired.class)) return true;

        // 세션 사용자를 DB에서 다시 읽어 현재 권한으로 판단
        var userId = SessionUser.id(request).orElseThrow(() -> ApiException.unauthorized("로그인이 필요합니다."));
        var user = users.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> ApiException.unauthorized("다시 로그인해주세요."));
        if (adminRequired && !user.isAdmin()) throw ApiException.forbidden("운영자 권한이 필요합니다.");
        request.setAttribute(CurrentUser.REQUEST_ATTRIBUTE, CurrentUser.from(user));
        return true;
    }

    /**
     * 인증된 현재 사용자 조회
     *
     * @param request 현재 요청
     * @return 인증을 거친 요청이면 현재 사용자, 아니면 {@code null}
     */
    public static CurrentUser current(HttpServletRequest request) {
        return (CurrentUser) request.getAttribute(CurrentUser.REQUEST_ATTRIBUTE);
    }

    /**
     * 핸들러 메서드 또는 컨트롤러 클래스의 어노테이션 여부
     *
     * @param method 핸들러 메서드
     * @param type 찾을 어노테이션 타입
     * @return 메서드나 클래스에 있으면 {@code true}
     */
    private static boolean annotated(HandlerMethod method, Class<? extends Annotation> type) {
        return method.hasMethodAnnotation(type) || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), type);
    }
}

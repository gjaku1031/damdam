package io.github.gjaku1031.damdam.common.auth;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import io.github.gjaku1031.damdam.common.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 인증된 핸들러의 {@link CurrentUser} 매개변수 해석기
 */
public final class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {
    /**
     * {@link CurrentUser} 타입 매개변수만 처리
     *
     * @param parameter 핸들러 매개변수
     * @return 매개변수 타입이 {@link CurrentUser}면 {@code true}
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == CurrentUser.class;
    }

    /**
     * {@link AuthInterceptor}가 요청에 담은 현재 사용자 반환
     *
     * @param parameter 핸들러 매개변수
     * @param container 모델·뷰 컨테이너
     * @param request 현재 요청
     * @param binderFactory 데이터 바인더 팩터리
     * @return 현재 사용자
     * @throws ApiException 인증을 거치지 않은 요청일 때(401)
     */
    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container, NativeWebRequest request,
            WebDataBinderFactory binderFactory) {
        CurrentUser user = AuthInterceptor.current(request.getNativeRequest(HttpServletRequest.class));
        // 핸들러에 @LoginRequired/@AdminOnly가 빠진 경우를 막는 안전장치
        if (user == null) throw ApiException.unauthorized("로그인이 필요합니다.");
        return user;
    }
}

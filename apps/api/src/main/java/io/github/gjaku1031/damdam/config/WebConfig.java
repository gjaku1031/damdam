package io.github.gjaku1031.damdam.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.pattern.PathPatternParser;

import io.github.gjaku1031.damdam.common.auth.AuthInterceptor;
import io.github.gjaku1031.damdam.common.auth.CsrfInterceptor;
import io.github.gjaku1031.damdam.common.auth.CurrentUserArgumentResolver;
import lombok.RequiredArgsConstructor;

/**
 * Spring MVC 설정
 *
 * 모든 컨트롤러를 {@code /api} 아래에 두고, 예전 Express API처럼 경로 대소문자를 구분하지 않음
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    /**
     * CSRF 토큰 검사기
     */
    private final CsrfInterceptor csrfInterceptor;

    /**
     * 로그인·운영자 권한 검사기
     */
    private final AuthInterceptor authInterceptor;

    /**
     * 웹 출처 설정
     */
    private final DamdamProperties properties;

    /**
     * 대소문자 무시 경로 매칭과 {@code /api} 접두사 등록
     *
     * @param configurer 경로 매칭 설정
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        PathPatternParser parser = new PathPatternParser();
        parser.setCaseSensitive(false);
        configurer.setPatternParser(parser);
        configurer.addPathPrefix("/api", HandlerTypePredicate.forAnnotation(RestController.class));
    }

    /**
     * CSRF·인증 인터셉터 등록
     *
     * 토큰 검사를 먼저 두어, 위조된 요청은 계정 조회 전에 거부
     *
     * @param registry 인터셉터 목록
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(csrfInterceptor);
        registry.addInterceptor(authInterceptor);
    }

    /**
     * {@code CurrentUser} 매개변수 해석기 등록
     *
     * @param resolvers 매개변수 해석기 목록
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }

    /**
     * 웹 출처 하나에 대해 쿠키 포함 CORS 허용
     *
     * @param registry CORS 매핑 목록
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(properties.webOrigin())
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                .allowCredentials(true);
    }
}

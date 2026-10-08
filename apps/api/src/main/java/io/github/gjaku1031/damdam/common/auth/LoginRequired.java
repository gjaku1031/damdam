package io.github.gjaku1031.damdam.common.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로그인한(삭제되지 않은) 사용자만 호출 가능한 핸들러 표시
 *
 * 컨트롤러 클래스에 붙이면 모든 핸들러에 적용
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginRequired {}

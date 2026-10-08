package io.github.gjaku1031.damdam.common.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 운영자만 호출 가능한 핸들러 표시
 *
 * {@code /api/admin/**} 경로는 이 표시가 없어도 항상 운영자 권한 검사
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminOnly {}

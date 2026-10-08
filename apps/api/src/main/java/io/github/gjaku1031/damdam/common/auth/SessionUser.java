package io.github.gjaku1031.damdam.common.auth;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * PostgreSQL 세션(Spring Session JDBC)에 저장하는 로그인 정보의 키와 조회 도우미
 */
public final class SessionUser {
    /**
     * 사용자 ID 세션 속성 키, 값은 UUID 문자열
     */
    public static final String USER_ID = "userId";

    /**
     * 권한 세션 속성 키, 값은 {@code UserRole} 이름
     */
    public static final String ROLE = "role";

    /**
     * 세션 사용자 ID의 UUID 형식
     */
    private static final Pattern UUID_FORMAT = Pattern.compile("^[\\da-f-]{36}$", Pattern.CASE_INSENSITIVE);

    /**
     * 정적 도우미 전용, 인스턴스 생성 금지
     */
    private SessionUser() {}

    /**
     * 세션에 저장된 사용자 ID 조회
     *
     * 세션을 새로 만들지 않음
     *
     * @param request 현재 요청
     * @return 사용자 ID, 세션이 없거나 값이 UUID 형식이 아니면 빈 값
     */
    public static Optional<UUID> id(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(USER_ID) instanceof String value)
                || !UUID_FORMAT.matcher(value).matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }
}

package io.github.gjaku1031.damdam.auth.dto;

/**
 * 아이디·비밀번호 입력 규칙
 */
public final class AuthRules {
    /**
     * 아이디 형식, 영문 소문자·숫자·밑줄 4~20자
     */
    public static final String USERNAME = "^[a-z0-9_]{4,20}$";

    /**
     * 아이디 형식 오류 메시지
     */
    public static final String USERNAME_MESSAGE = "아이디는 영문·숫자·밑줄 4~20자로 입력해주세요.";

    /**
     * 상수 전용, 인스턴스 생성 금지
     */
    private AuthRules() {}
}

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
     * 새 비밀번호 형식, 영문과 숫자를 하나 이상 포함한 8~128자
     *
     * 특수문자·한글 등 다른 문자는 막지 않음, 128자 상한은 해시 계산 비용을 제한하기 위한 값
     */
    public static final String PASSWORD = "^(?=.*[A-Za-z])(?=.*[0-9]).{8,128}$";

    /**
     * 새 비밀번호 형식 오류 메시지
     */
    public static final String PASSWORD_MESSAGE = "비밀번호는 영문과 숫자를 포함해 8자 이상으로 입력해주세요.";

    /**
     * 상수 전용, 인스턴스 생성 금지
     */
    private AuthRules() {}
}

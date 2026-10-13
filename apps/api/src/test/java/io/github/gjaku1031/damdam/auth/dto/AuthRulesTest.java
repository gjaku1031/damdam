package io.github.gjaku1031.damdam.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * 아이디·비밀번호 입력 규칙
 */
final class AuthRulesTest {
    /**
     * 아이디 형식
     */
    private static final Pattern USERNAME = Pattern.compile(AuthRules.USERNAME);

    /**
     * 비밀번호 형식
     */
    private static final Pattern PASSWORD = Pattern.compile(AuthRules.PASSWORD);

    /**
     * 아이디는 영문 소문자·숫자·밑줄 4~20자
     */
    @Test
    void usernameAllowsLowercaseDigitsAndUnderscore() {
        assertThat(USERNAME.matcher("abcd").matches()).isTrue();
        assertThat(USERNAME.matcher("user_01").matches()).isTrue();
        assertThat(USERNAME.matcher("a".repeat(20)).matches()).isTrue();
        assertThat(USERNAME.matcher("abc").matches()).as("3자").isFalse();
        assertThat(USERNAME.matcher("a".repeat(21)).matches()).as("21자").isFalse();
        assertThat(USERNAME.matcher("Admin").matches()).as("대문자").isFalse();
        assertThat(USERNAME.matcher("user-01").matches()).as("하이픈").isFalse();
        assertThat(USERNAME.matcher("한글아이디").matches()).as("한글").isFalse();
        assertThat(USERNAME.matcher("user 01").matches()).as("공백").isFalse();
    }

    /**
     * 비밀번호는 영문과 숫자를 하나 이상 포함한 8~128자, 특수문자와 한글은 막지 않음
     */
    @Test
    void passwordNeedsLetterAndDigitWithinLength() {
        assertThat(PASSWORD.matcher("abcdefg1").matches()).isTrue();
        assertThat(PASSWORD.matcher("비밀번호a1!@#").matches()).isTrue();
        assertThat(PASSWORD.matcher("a1" + "x".repeat(126)).matches()).as("128자").isTrue();
        assertThat(PASSWORD.matcher("abcdef1").matches()).as("7자").isFalse();
        assertThat(PASSWORD.matcher("abcdefgh").matches()).as("숫자 없음").isFalse();
        assertThat(PASSWORD.matcher("12345678").matches()).as("영문 없음").isFalse();
        assertThat(PASSWORD.matcher("a1" + "x".repeat(127)).matches()).as("129자").isFalse();
        assertThat(PASSWORD.matcher("admin").matches()).isFalse();
    }
}

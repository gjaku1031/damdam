package io.github.gjaku1031.damdam.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Argon2id 비밀번호 해시
 */
final class PasswordHasherTest {
    /**
     * 검증 대상
     */
    private final PasswordHasher hasher = new PasswordHasher();

    /**
     * 해시는 설정한 비용의 Argon2id PHC 문자열이고 같은 비밀번호만 일치
     */
    @Test
    void hashesWithConfiguredArgon2idParameters() {
        String hash = hasher.hash("correct-pass-1");

        assertThat(hash).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
        assertThat(hasher.matches(hash, "correct-pass-1")).isTrue();
        assertThat(hasher.matches(hash, "wrong-pass-1")).isFalse();
        assertThat(hasher.isCurrentFormat(hash)).isTrue();
    }

    /**
     * 같은 비밀번호도 솔트가 달라 해시가 매번 다름
     */
    @Test
    void usesFreshSaltForEachHash() {
        assertThat(hasher.hash("same-pass-1")).isNotEqualTo(hasher.hash("same-pass-1"));
    }

    /**
     * 해시가 없거나 Argon2id 형식이 아니거나 손상됐으면 예외 없이 불일치
     */
    @Test
    void treatsMissingForeignOrBrokenHashAsMismatch() {
        assertThat(hasher.matches(null, "any-pass-1")).isFalse();
        assertThat(hasher.matches("5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8", "password")).isFalse();
        assertThat(hasher.matches("$argon2id$v=19$m=19456,t=2,p=1$broken", "any-pass-1")).isFalse();
        assertThat(hasher.isCurrentFormat(null)).isFalse();
        assertThat(hasher.isCurrentFormat("$2a$10$abcdefghijklmnopqrstuv")).isFalse();
    }
}

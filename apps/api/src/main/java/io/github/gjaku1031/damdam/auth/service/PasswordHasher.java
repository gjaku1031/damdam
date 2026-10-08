package io.github.gjaku1031.damdam.auth.service;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Argon2id 비밀번호 해시 (OWASP 권장값: 19 MiB, 2회, 병렬 1)
 *
 * 저장된 PHC 문자열({@code $argon2id$v=19$m=19456,t=2,p=1$...}) 검증
 */
@Component
public final class PasswordHasher {
    /**
     * 현재 형식 해시의 접두사
     */
    private static final String PREFIX = "$argon2id$";

    /**
     * 솔트 16바이트, 해시 32바이트, 병렬 1, 메모리 19456 KiB, 반복 2회 인코더
     */
    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);

    /**
     * 비밀번호 해시 생성
     *
     * @param password 평문 비밀번호
     * @return PHC 형식 Argon2id 해시
     */
    public String hash(String password) {
        return encoder.encode(password);
    }

    /**
     * 비밀번호 일치 여부
     *
     * @param encoded 저장된 해시
     * @param password 입력한 평문 비밀번호
     * @return 일치하면 {@code true}, 해시가 없거나 Argon2id 형식이 아니거나 손상됐으면 {@code false}
     */
    public boolean matches(String encoded, String password) {
        if (encoded == null || !encoded.startsWith(PREFIX)) return false;
        try {
            return encoder.matches(password, encoded);
        } catch (RuntimeException malformed) {
            return false;
        }
    }

    /**
     * 현재 해시 형식 여부
     *
     * @param encoded 저장된 해시
     * @return Argon2id PHC 문자열이면 {@code true}
     */
    public boolean isCurrentFormat(String encoded) {
        return encoded != null && encoded.startsWith(PREFIX);
    }
}

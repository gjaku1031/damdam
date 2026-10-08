package io.github.gjaku1031.damdam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code damdam.*} 애플리케이션 설정
 *
 * @param webOrigin CORS를 허용할 웹 출처
 * @param trustProxy {@code X-Forwarded-For}를 신뢰할지 여부
 * @param seed 초기 운영자 계정 설정
 * @param storage 사진 저장 경로 설정
 */
@ConfigurationProperties("damdam")
public record DamdamProperties(String webOrigin, boolean trustProxy, Seed seed, Storage storage) {
    /**
     * 초기 운영자 계정 설정
     *
     * @param adminUsername 운영자 아이디, {@code null}이면 {@code admin}
     * @param adminPassword 운영자 비밀번호, 비어 있으면 운영자를 준비하지 않음
     */
    public record Seed(String adminUsername, String adminPassword) {}

    /**
     * 사진 저장 경로 설정
     *
     * @param localPath 업로드 사진 저장 디렉터리
     * @param legacyImagesPath 기존 정적 사진 디렉터리
     */
    public record Storage(String localPath, String legacyImagesPath) {}
}

package io.github.gjaku1031.damdam.storage.service;

import java.util.regex.Pattern;

/**
 * 업로드·기존 정적 사진 키의 허용 형식
 */
public final class ImageKeys {
    /**
     * 업로드 사진 키 형식 {@code post|product/<uuid>.<ext>}
     */
    static final Pattern UPLOADED = Pattern.compile("^(post|product)/[\\da-f-]{36}\\.(jpg|jpeg|png|webp)$",
            Pattern.CASE_INSENSITIVE);

    /**
     * 본문에 연결 가능한 사진 키 형식: 업로드 사진 또는 웹의 기존 정적 사진({@code /images/...})
     */
    public static final Pattern ATTACHABLE = Pattern.compile(
            "^((post|product)/[\\da-f-]{36}\\.(jpg|jpeg|png|webp)|/images/[\\w./-]+\\.(jpg|jpeg|png|webp))$",
            Pattern.CASE_INSENSITIVE);

    /**
     * 업로드 사진을 제공하는 공개 URL 접두사
     */
    public static final String FILE_URL_PREFIX = "/api/storage/files/";

    /**
     * 기존 정적 사진 키 접두사
     */
    static final String LEGACY_PREFIX = "/images/";

    /**
     * 인스턴스 생성 방지
     */
    private ImageKeys() {}

    /**
     * 본문에 넣을 사진 주소
     *
     * @param key 업로드 사진 키 또는 {@code /images/...} 경로
     * @return 기존 정적 사진은 그대로, 업로드 사진은 파일 API 주소
     */
    public static String publicUrl(String key) {
        return key.startsWith(LEGACY_PREFIX) ? key : FILE_URL_PREFIX + key;
    }

    /**
     * 파일명 확장자로 사진 MIME 형식 판단
     *
     * @return PNG·WebP가 아니면 {@code image/jpeg}
     */
    public static String contentType(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".png") ? "image/png" : lower.endsWith(".webp") ? "image/webp" : "image/jpeg";
    }
}

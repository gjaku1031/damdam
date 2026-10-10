package io.github.gjaku1031.damdam.storage.service;

import java.util.Locale;
import java.util.regex.Pattern;

import io.github.gjaku1031.damdam.storage.domain.ImageVariant;

/**
 * 업로드·기존 정적 사진 키의 허용 형식과 변환본 이름 규칙
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
     * 기존 정적 사진 파일명 형식({@code /images/} 아래 상대 경로)
     */
    static final Pattern LEGACY_FILENAME = Pattern.compile("^[\\w./-]+\\.(jpg|jpeg|png|webp)$", Pattern.CASE_INSENSITIVE);

    /**
     * 변환본의 MIME 형식
     */
    public static final String VARIANT_CONTENT_TYPE = "image/webp";

    /**
     * 기존 정적 사진의 변환본을 업로드 저장소에 둘 때 쓰는 접두사
     */
    private static final String LEGACY_VARIANT_PREFIX = "legacy/";

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
     * 변환본의 저장 키
     *
     * 사진 키는 그대로 두고 이름 규칙으로 변환본을 찾음(DB에 변환본 목록을 두지 않음)
     * <ul>
     *   <li>업로드 사진 {@code product/<uuid>.jpg} → {@code product/<uuid>-s.webp}</li>
     *   <li>기존 정적 사진 {@code /images/a.jpg} → {@code legacy/a.jpg-s.webp}, 확장자만 다른 파일끼리 겹치지 않게 원래 확장자 유지</li>
     * </ul>
     *
     * @param key 업로드 사진 키 또는 {@code /images/...} 경로
     * @return 업로드 저장소 안의 변환본 키
     */
    static String variantKey(String key, ImageVariant variant) {
        String base = key.startsWith(LEGACY_PREFIX)
                ? LEGACY_VARIANT_PREFIX + key.substring(LEGACY_PREFIX.length())
                : key.substring(0, key.lastIndexOf('.'));
        return base + "-" + variant.code() + ".webp";
    }

    /**
     * 파일명 확장자로 사진 MIME 형식 판단
     *
     * @return PNG·WebP가 아니면 {@code image/jpeg}
     */
    public static String contentType(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") ? "image/png" : lower.endsWith(".webp") ? "image/webp" : "image/jpeg";
    }
}

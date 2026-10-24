package io.github.gjaku1031.damdam.storage.service;

import java.util.Locale;
import java.util.regex.Pattern;

import io.github.gjaku1031.damdam.storage.domain.ImageVariant;

/**
 * 업로드 사진 키의 허용 형식과 변환본 이름 규칙
 */
public final class ImageKeys {
    /**
     * 업로드 사진 키 형식 {@code post|product/<uuid>.<ext>}, 본문·상품에 연결할 수 있는 유일한 사진 형식
     */
    public static final Pattern UPLOADED = Pattern.compile("^(post|product)/[\\da-f-]{36}\\.(jpg|jpeg|png|webp)$",
            Pattern.CASE_INSENSITIVE);

    /**
     * 변환본의 MIME 형식
     */
    public static final String VARIANT_CONTENT_TYPE = "image/webp";

    /**
     * 업로드 사진을 제공하는 공개 URL 접두사
     */
    public static final String FILE_URL_PREFIX = "/api/storage/files/";

    /**
     * 인스턴스 생성 방지
     */
    private ImageKeys() {}

    /**
     * 본문에 넣을 사진 주소
     *
     * @param key 업로드 사진 키
     * @return 파일 API 주소
     */
    public static String publicUrl(String key) {
        return FILE_URL_PREFIX + key;
    }

    /**
     * 변환본의 저장 키
     *
     * 사진 키는 그대로 두고 이름 규칙으로 변환본을 찾음(DB에 변환본 목록을 두지 않음)
     * 예: {@code product/<uuid>.jpg} → {@code product/<uuid>-s.webp}
     *
     * @param key 업로드 사진 키
     * @return 업로드 저장소 안의 변환본 키
     */
    static String variantKey(String key, ImageVariant variant) {
        return key.substring(0, key.lastIndexOf('.')) + "-" + variant.code() + ".webp";
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

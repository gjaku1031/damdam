package io.github.gjaku1031.damdam.storage.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.gjaku1031.damdam.storage.domain.ImageVariant;

/**
 * 사진 키의 허용 형식과 변환본 이름 규칙
 */
final class ImageKeysTest {
    /**
     * 업로드 사진 키는 {@code post|product/<uuid>.<확장자>}만 허용, 경로 이동이나 다른 폴더는 거부
     */
    @Test
    void uploadedKeyMustBeDomainUuidAndImageExtension() {
        assertThat(matchesUploaded("product/4f47b3c4-1516-4698-90db-7f4aeeb51b14.jpg")).isTrue();
        assertThat(matchesUploaded("post/6c40f107-0254-4e7e-97a5-dc2c2788c3c0.PNG")).isTrue();
        assertThat(matchesUploaded("product/4f47b3c4-1516-4698-90db-7f4aeeb51b14-s.webp")).as("변환본 파일 이름").isFalse();
        assertThat(matchesUploaded("legacy/a.jpg")).isFalse();
        assertThat(matchesUploaded("product/../secret.jpg")).isFalse();
        assertThat(matchesUploaded("product/4f47b3c4-1516-4698-90db-7f4aeeb51b14.gif")).isFalse();
    }

    /**
     * 본문에는 업로드 사진과 기존 정적 사진({@code /images/...})만 넣을 수 있고 외부 주소는 거부
     */
    @Test
    void attachableKeyAllowsUploadsAndLegacyImagesOnly() {
        assertThat(ImageKeys.ATTACHABLE.matcher("post/6c40f107-0254-4e7e-97a5-dc2c2788c3c0.png").matches()).isTrue();
        assertThat(ImageKeys.ATTACHABLE.matcher("/images/products/chair.jpg").matches()).isTrue();
        assertThat(ImageKeys.ATTACHABLE.matcher("https://evil.example/a.jpg").matches()).isFalse();
        assertThat(ImageKeys.ATTACHABLE.matcher("/api/admin/orders").matches()).isFalse();
    }

    /**
     * 업로드 사진의 변환본은 확장자를 떼고 크기 코드를 붙인 WebP
     */
    @Test
    void variantKeyOfUploadReplacesExtension() {
        String key = "product/4f47b3c4-1516-4698-90db-7f4aeeb51b14.jpg";

        assertThat(ImageKeys.variantKey(key, ImageVariant.SMALL)).isEqualTo("product/4f47b3c4-1516-4698-90db-7f4aeeb51b14-s.webp");
        assertThat(ImageKeys.variantKey(key, ImageVariant.MEDIUM)).isEqualTo("product/4f47b3c4-1516-4698-90db-7f4aeeb51b14-m.webp");
        assertThat(ImageKeys.variantKey(key, ImageVariant.LARGE)).isEqualTo("product/4f47b3c4-1516-4698-90db-7f4aeeb51b14-l.webp");
    }

    /**
     * 기존 정적 사진의 변환본은 {@code legacy/} 아래에 원래 확장자를 남겨, 확장자만 다른 파일끼리 겹치지 않음
     */
    @Test
    void variantKeyOfLegacyImageKeepsOriginalExtension() {
        assertThat(ImageKeys.variantKey("/images/chair.jpg", ImageVariant.SMALL)).isEqualTo("legacy/chair.jpg-s.webp");
        assertThat(ImageKeys.variantKey("/images/chair.png", ImageVariant.SMALL)).isEqualTo("legacy/chair.png-s.webp");
    }

    /**
     * 업로드 사진은 파일 API 주소로, 기존 정적 사진은 경로 그대로 내보냄
     */
    @Test
    void publicUrlPrefixesUploadsOnly() {
        assertThat(ImageKeys.publicUrl("post/6c40f107-0254-4e7e-97a5-dc2c2788c3c0.png"))
                .isEqualTo("/api/storage/files/post/6c40f107-0254-4e7e-97a5-dc2c2788c3c0.png");
        assertThat(ImageKeys.publicUrl("/images/chair.jpg")).isEqualTo("/images/chair.jpg");
    }

    /**
     * MIME 형식은 확장자로 판단하고 PNG·WebP가 아니면 JPEG
     */
    @Test
    void contentTypeFollowsExtension() {
        assertThat(ImageKeys.contentType("a.PNG")).isEqualTo("image/png");
        assertThat(ImageKeys.contentType("a.webp")).isEqualTo("image/webp");
        assertThat(ImageKeys.contentType("a.jpeg")).isEqualTo("image/jpeg");
    }

    /**
     * 업로드 사진 키 형식 일치 여부
     */
    private static boolean matchesUploaded(String key) {
        return ImageKeys.UPLOADED.matcher(key).matches();
    }
}

package io.github.gjaku1031.damdam.storage.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.gjaku1031.damdam.storage.domain.ImageVariant;

/**
 * 사진 키의 허용 형식과 변환본 이름 규칙
 */
final class ImageKeysTest {
    /**
     * 업로드 사진 키는 {@code post|product/<uuid>.<확장자>}만 허용, 경로 이동·다른 폴더·외부 주소는 거부
     */
    @Test
    void uploadedKeyMustBeDomainUuidAndImageExtension() {
        assertThat(matchesUploaded("product/4f47b3c4-1516-4698-90db-7f4aeeb51b14.jpg")).isTrue();
        assertThat(matchesUploaded("post/6c40f107-0254-4e7e-97a5-dc2c2788c3c0.PNG")).isTrue();
        assertThat(matchesUploaded("product/4f47b3c4-1516-4698-90db-7f4aeeb51b14-s.webp")).as("변환본 파일 이름").isFalse();
        assertThat(matchesUploaded("product/../secret.jpg")).isFalse();
        assertThat(matchesUploaded("product/4f47b3c4-1516-4698-90db-7f4aeeb51b14.gif")).isFalse();
        assertThat(matchesUploaded("/images/products/chair.jpg")).as("웹 정적 경로").isFalse();
        assertThat(matchesUploaded("https://evil.example/a.jpg")).isFalse();
        assertThat(matchesUploaded("/api/admin/orders")).isFalse();
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
     * 업로드 사진은 파일 API 주소로 내보냄
     */
    @Test
    void publicUrlPrefixesFileApi() {
        assertThat(ImageKeys.publicUrl("post/6c40f107-0254-4e7e-97a5-dc2c2788c3c0.png"))
                .isEqualTo("/api/storage/files/post/6c40f107-0254-4e7e-97a5-dc2c2788c3c0.png");
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

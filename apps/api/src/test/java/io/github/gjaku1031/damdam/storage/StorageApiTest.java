package io.github.gjaku1031.damdam.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.gjaku1031.damdam.fixture.ApiClient;
import io.github.gjaku1031.damdam.fixture.ApiResponse;
import io.github.gjaku1031.damdam.fixture.IntegrationTest;
import tools.jackson.databind.JsonNode;

/**
 * 사진 업로드 검증과 사진 접근 권한·캐시 정책
 */
final class StorageApiTest extends IntegrationTest {
    /**
     * 업로드는 운영자만 할 수 있음
     */
    @Test
    void onlyOperatorsCanUpload() {
        Map<String, Object> ticket = Map.of("domain", "PRODUCT", "contentType", "image/png", "sizeBytes", PNG.length);

        assertThat(visitor().post("/storage/tickets", ticket).status()).isEqualTo(401);
        assertThat(member().post("/storage/tickets", ticket).status()).isEqualTo(403);
        assertThat(admin().post("/storage/tickets", ticket).status()).isEqualTo(201);
    }

    /**
     * 허용하지 않는 형식과 5MB를 넘는 크기는 티켓 발급에서 400
     */
    @Test
    void rejectsUnsupportedTypeAndOversizedTicket() {
        ApiClient admin = admin();

        assertThat(admin.post("/storage/tickets", Map.of("domain", "PRODUCT", "contentType", "image/gif", "sizeBytes", 10))
                .status()).isEqualTo(400);
        assertThat(admin.post("/storage/tickets",
                Map.of("domain", "PRODUCT", "contentType", "image/png", "sizeBytes", 5 * 1024 * 1024 + 1)).status())
                .isEqualTo(400);
    }

    /**
     * 신고한 형식·크기와 실제 파일이 다르면 400이고, 티켓은 한 번만 쓸 수 있음
     */
    @Test
    void uploadMustMatchTicketAndTicketIsSingleUse() {
        ApiClient admin = admin();
        byte[] notAnImage = "<script>alert(1)</script>".getBytes();
        String forged = ticket(admin, notAnImage.length);
        String wrongSize = ticket(admin, PNG.length + 1);
        String valid = ticket(admin, PNG.length);

        assertThat(admin.upload(forged, notAnImage, "image/png").status()).as("사진이 아닌 내용").isEqualTo(400);
        assertThat(admin.upload(wrongSize, PNG, "image/png").status()).as("신고와 다른 크기").isEqualTo(400);
        created(admin.upload(valid, PNG, "image/png"));
        assertThat(admin.upload(valid, PNG, "image/png").status()).as("이미 쓴 티켓").isEqualTo(400);
    }

    /**
     * 업로드를 마치지 않은 키나 외부 주소는 상품에 붙일 수 없음
     */
    @Test
    void onlyVerifiedUploadsCanBeAttached() {
        ApiClient admin = admin();
        JsonNode pending = created(admin.post("/storage/tickets",
                Map.of("domain", "PRODUCT", "contentType", "image/png", "sizeBytes", PNG.length))).body();

        assertThat(attach(admin, pending.path("objectKey").asString()).status()).isEqualTo(400);
        assertThat(attach(admin, "https://evil.example/a.png").status()).isEqualTo(400);
        assertThat(attach(admin, uploadPhoto(admin, "PRODUCT")).status()).isEqualTo(201);
    }

    /**
     * 아직 어디에도 붙이지 않은 사진은 운영자만 볼 수 있고 저장 금지로 응답
     */
    @Test
    void unattachedPhotoIsOperatorOnly() {
        ApiClient admin = admin();
        String key = uploadPhoto(admin, "PRODUCT");

        assertThat(visitor().get("/storage/files/" + key).status()).isEqualTo(404);
        assertThat(member().get("/storage/files/" + key).status()).isEqualTo(404);
        ApiResponse forOperator = ok(admin.get("/storage/files/" + key));
        assertThat(forOperator.bytes()).isEqualTo(PNG);
        assertThat(forOperator.header("Cache-Control")).isEqualTo("no-store");
    }

    /**
     * 공개 상품의 사진은 누구나 볼 수 있고 캐시를 허용, 상품을 숨기면 다시 운영자만 볼 수 있음
     */
    @Test
    void publicProductPhotoIsCacheableUntilHidden() {
        ApiClient admin = admin();
        String key = uploadPhoto(admin, "PRODUCT");
        JsonNode product = product(admin, 1, key);

        ApiResponse open = ok(visitor().get("/storage/files/" + key));
        assertThat(open.header("Cache-Control")).contains("public").contains("max-age=86400");
        assertThat(open.header("Content-Type")).isEqualTo("image/png");

        created(admin.post("/admin/products/" + product.path("id").asString() + "/hide", null));
        assertThat(visitor().get("/storage/files/" + key).status()).isEqualTo(404);
        assertThat(ok(admin.get("/storage/files/" + key)).header("Cache-Control")).isEqualTo("no-store");
    }

    /**
     * 숨긴 상품이어도 그 상품을 주문한 회원은 주문 내역의 대표 사진을 볼 수 있고, 다른 회원은 볼 수 없음
     */
    @Test
    void buyerKeepsAccessToOrderedProductPhoto() {
        ApiClient admin = admin();
        String key = uploadPhoto(admin, "PRODUCT");
        JsonNode product = product(admin, 2, key);
        ApiClient buyer = member();
        JsonNode order = created(buyer.post("/orders", orderOf(variantId(product), 1))).body();
        created(admin.post("/admin/products/" + product.path("id").asString() + "/hide", null));

        assertThat(order.path("items").path(0).path("thumbnailImageUrlSnapshot").asString()).isEqualTo(key);
        ApiResponse forBuyer = ok(buyer.get("/storage/files/" + key));
        assertThat(forBuyer.header("Cache-Control")).isEqualTo("no-store");
        assertThat(member().get("/storage/files/" + key).status()).isEqualTo(404);
    }

    /**
     * 형식에 맞지 않는 키와 잘못된 크기 값은 파일 존재 여부를 드러내지 않고 404
     */
    @Test
    void malformedKeyOrSizeIsNotFound() {
        ApiClient admin = admin();
        String key = uploadPhoto(admin, "PRODUCT");

        assertThat(admin.get("/storage/files/" + key + "?size=x").status()).isEqualTo(404);
        assertThat(admin.get("/storage/files/" + key.replace(".png", "-s.webp")).status()).isEqualTo(404);
        assertThat(admin.get("/storage/files/product/not-a-uuid.png").status()).isEqualTo(404);
        assertThat(admin.get("/storage/files/" + key + "?size=s").status()).as("변환본이 없으면 원본").isEqualTo(200);
    }

    /**
     * 지정한 크기로 발급한 PNG 업로드 티켓 ID
     */
    private String ticket(ApiClient admin, int sizeBytes) {
        return created(admin.post("/storage/tickets",
                Map.of("domain", "PRODUCT", "contentType", "image/png", "sizeBytes", sizeBytes))).body().path("id").asString();
    }

    /**
     * 사진 키 하나를 붙여 상품 등록 시도
     */
    private ApiResponse attach(ApiClient admin, String key) {
        return admin.post("/admin/products", Map.of("name", "사진 검증 " + uniqueName(), "visible", true,
                "images", List.of(Map.of("objectKey", key)),
                "variants", List.of(Map.of("name", "기본", "priceAmount", 1000, "stock", 1, "isDefault", true,
                        "active", true))));
    }
}

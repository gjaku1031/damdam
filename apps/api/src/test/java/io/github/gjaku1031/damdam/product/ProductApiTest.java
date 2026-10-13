package io.github.gjaku1031.damdam.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.gjaku1031.damdam.fixture.ApiClient;
import io.github.gjaku1031.damdam.fixture.IntegrationTest;
import tools.jackson.databind.JsonNode;

/**
 * 상품 등록·수정·공개 여부·삭제
 */
final class ProductApiTest extends IntegrationTest {
    /**
     * 공개 상품만 방문자에게 보이고, 숨긴 상품은 운영자만 조회 가능
     */
    @Test
    void hiddenProductIsVisibleToOperatorOnly() {
        ApiClient admin = admin();
        String open = product(admin, 1).path("id").asString();
        String hidden = product(admin, 1, false).path("id").asString();

        assertThat(visitor().get("/products/" + open).status()).isEqualTo(200);
        assertThat(visitor().get("/products/" + hidden).status()).isEqualTo(404);
        assertThat(admin.get("/admin/products/" + hidden).status()).isEqualTo(200);
        assertThat(ok(visitor().get("/products?limit=100")).body().path("products").findValuesAsString("id"))
                .contains(open).doesNotContain(hidden);
    }

    /**
     * 숨기기·보이기는 바로 반영
     */
    @Test
    void showAndHideTakeEffectImmediately() {
        ApiClient admin = admin();
        String id = product(admin, 1).path("id").asString();

        created(admin.post("/admin/products/" + id + "/hide", null));
        assertThat(visitor().get("/products/" + id).status()).isEqualTo(404);
        created(admin.post("/admin/products/" + id + "/show", null));
        assertThat(visitor().get("/products/" + id).status()).isEqualTo(200);
    }

    /**
     * 삭제한 상품은 방문자와 운영자 모두에게 404이고, 그 옵션은 더 주문할 수 없음
     */
    @Test
    void deletedProductDisappearsAndCannotBeOrdered() {
        ApiClient admin = admin();
        JsonNode product = product(admin, 3);
        String id = product.path("id").asString();

        ok(admin.delete("/admin/products/" + id));

        assertThat(visitor().get("/products/" + id).status()).isEqualTo(404);
        assertThat(admin.delete("/admin/products/" + id).status()).isEqualTo(404);
        assertThat(member().post("/orders", orderOf(variantId(product), 1)).status()).isEqualTo(400);
    }

    /**
     * 상품 수정으로는 기존 옵션의 재고를 바꿀 수 없음(재고는 이력이 남는 재고 조정으로만 변경)
     */
    @Test
    void editingProductCannotChangeExistingStock() {
        ApiClient admin = admin();
        JsonNode product = product(admin, 5);
        String variant = variantId(product);

        int status = admin.patch("/admin/products/" + product.path("id").asString(), Map.of("variants",
                List.of(Map.of("id", variant, "name", "기본", "priceAmount", 1000, "stock", 9, "isDefault", true,
                        "active", true)))).status();

        assertThat(status).isEqualTo(409);
        assertThat(stockOf(variant)).isEqualTo(5);
    }

    /**
     * 가격을 바꾸면 새 주문부터 적용되고, 이미 받은 주문의 금액은 그대로
     */
    @Test
    void priceChangeAppliesToNewOrdersOnly() {
        ApiClient admin = admin();
        JsonNode product = product(admin, 5);
        String variant = variantId(product);
        ApiClient member = member();
        String before = created(member.post("/orders", orderOf(variant, 1))).body().path("id").asString();

        ok(admin.patch("/admin/products/" + product.path("id").asString(), Map.of("variants",
                List.of(Map.of("id", variant, "name", "기본", "priceAmount", 2500, "stock", 4, "isDefault", true,
                        "active", true)))));
        JsonNode after = created(member.post("/orders", orderOf(variant, 1))).body();

        assertThat(after.path("totalAmount").asInt()).isEqualTo(2500);
        assertThat(ok(member.get("/orders/" + before)).body().path("totalAmount").asInt()).isEqualTo(1000);
    }

    /**
     * 상품명이 비었거나 판매 중인 옵션이 하나도 없으면 400
     */
    @Test
    void rejectsBlankNameAndProductWithoutActiveOption() {
        ApiClient admin = admin();
        Map<String, Object> option = Map.of("name", "기본", "priceAmount", 1000, "stock", 1, "isDefault", true, "active", true);
        Map<String, Object> inactive = Map.of("name", "기본", "priceAmount", 1000, "stock", 1, "isDefault", true,
                "active", false);

        assertThat(admin.post("/admin/products",
                Map.of("name", " ", "visible", true, "images", List.of(), "variants", List.of(option))).status())
                .isEqualTo(400);
        assertThat(admin.post("/admin/products",
                Map.of("name", "옵션 검증", "visible", true, "images", List.of(), "variants", List.of(inactive))).status())
                .isEqualTo(400);
    }

    /**
     * 기본 옵션을 지정하지 않으면 첫 번째 옵션이 기본 옵션이 됨
     */
    @Test
    void firstOptionBecomesDefaultWhenNoneIsMarked() {
        Map<String, Object> first = Map.of("name", "소", "priceAmount", 1000, "stock", 1, "isDefault", false, "active", true);
        Map<String, Object> second = Map.of("name", "대", "priceAmount", 2000, "stock", 1, "isDefault", false, "active", true);

        JsonNode product = created(admin().post("/admin/products", Map.of("name", "기본 옵션 검증 " + uniqueName(),
                "visible", true, "images", List.of(), "variants", List.of(first, second)))).body();

        assertThat(product.path("variants").findValues("isDefault").stream().filter(JsonNode::asBoolean)).hasSize(1);
        JsonNode defaultOption = product.path("variants").path(0).path("isDefault").asBoolean()
                ? product.path("variants").path(0) : product.path("variants").path(1);
        assertThat(defaultOption.path("name").asString()).isEqualTo("소");
    }

    /**
     * 분류는 운영자가 먼저 등록한 것만 상품에 지정할 수 있음
     */
    @Test
    void categoryMustBeRegisteredFirst() {
        ApiClient admin = admin();
        String category = "분류" + uniqueName().substring(0, 8);
        Map<String, Object> option = Map.of("name", "기본", "priceAmount", 1000, "stock", 1, "isDefault", true, "active", true);
        Map<String, Object> body = Map.of("name", "분류 검증 " + uniqueName(), "visible", true, "category", category,
                "images", List.of(), "variants", List.of(option));

        assertThat(admin.post("/admin/products", body).status()).isEqualTo(400);
        created(admin.post("/admin/products/categories", Map.of("name", category)));

        assertThat(created(admin.post("/admin/products", body)).body().path("category").asString()).isEqualTo(category);
        assertThat(ok(visitor().get("/products/categories")).body().toString()).contains(category);
    }
}

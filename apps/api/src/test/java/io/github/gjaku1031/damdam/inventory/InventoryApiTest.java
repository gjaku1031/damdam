package io.github.gjaku1031.damdam.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import io.github.gjaku1031.damdam.fixture.ApiClient;
import io.github.gjaku1031.damdam.fixture.ApiResponse;
import io.github.gjaku1031.damdam.fixture.IntegrationTest;
import io.github.gjaku1031.damdam.inventory.dto.StockLedgerMismatch;
import io.github.gjaku1031.damdam.inventory.service.InventoryService;
import tools.jackson.databind.JsonNode;

/**
 * 운영자 재고 조정과 재고·이력 대조
 */
final class InventoryApiTest extends IntegrationTest {
    /**
     * 대조 결과를 직접 조회하기 위한 재고 서비스
     */
    @Autowired
    private InventoryService inventory;

    /**
     * 새 옵션의 초기 재고도 이력으로 남아, 처음부터 이력 합계와 재고가 같음
     */
    @Test
    void initialStockIsRecordedInLedger() {
        ApiClient admin = admin();
        String variant = variantId(product(admin, 7));

        JsonNode history = ok(admin.get("/admin/inventory/variants/" + variant + "/history")).body();

        assertThat(history.path("items")).hasSize(1);
        assertThat(history.path("items").path(0).path("delta").asInt()).isEqualTo(7);
        assertThat(ledgerOf(variant)).isEqualTo(stockOf(variant)).isEqualTo(7);
    }

    /**
     * 조정하면 재고가 바뀌고 같은 변화량의 이력이 최근 순으로 쌓임
     */
    @Test
    void adjustmentChangesStockAndAppendsHistory() {
        ApiClient admin = admin();
        String variant = variantId(product(admin, 5));
        String path = "/admin/inventory/variants/" + variant + "/adjust";

        created(admin.post(path, Map.of("delta", 4, "reason", "입고")));
        JsonNode result = created(admin.post(path, Map.of("delta", -6, "reason", "파손"))).body();

        assertThat(result.path("stock").asInt()).isEqualTo(3);
        assertThat(stockOf(variant)).isEqualTo(3);
        assertThat(ledgerOf(variant)).isEqualTo(3);
        JsonNode items = ok(admin.get("/admin/inventory/variants/" + variant + "/history")).body().path("items");
        assertThat(items.path(0).path("reason").asString()).isEqualTo("파손");
        assertThat(items.path(1).path("reason").asString()).isEqualTo("입고");
    }

    /**
     * 재고가 음수가 되는 조정과 사유 없는 조정은 400이고 아무것도 바뀌지 않음
     */
    @Test
    void rejectsAdjustmentBelowZeroOrWithoutReason() {
        ApiClient admin = admin();
        String variant = variantId(product(admin, 2));
        String path = "/admin/inventory/variants/" + variant + "/adjust";

        ApiResponse belowZero = admin.post(path, Map.of("delta", -3, "reason", "출고"));
        ApiResponse noReason = admin.post(path, Map.of("delta", 1, "reason", " "));

        assertThat(belowZero.status()).isEqualTo(400);
        assertThat(noReason.status()).isEqualTo(400);
        assertThat(stockOf(variant)).isEqualTo(2);
        assertThat(ledgerOf(variant)).isEqualTo(2);
    }

    /**
     * DB의 재고 제약이 음수 재고를 직접 막음
     */
    @Test
    void databaseRejectsNegativeStock() {
        String variant = variantId(product(admin(), 1));

        assertThatThrownBy(() -> jdbc.update(
                "update \"product_variants\" set \"stock\" = -1 where \"id\" = ?::uuid", variant))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * 이력 없이 재고만 바뀐 옵션은 대조에 잡혀 대시보드에 나오고, 맞춰 놓으면 사라짐
     */
    @Test
    void ledgerCheckFlagsStockChangedWithoutHistory() {
        ApiClient admin = admin();
        String variant = variantId(product(admin, 5));
        assertThat(mismatchOf(variant)).as("정상 상태").isEmpty();

        jdbc.update("update \"product_variants\" set \"stock\" = \"stock\" + 3 where \"id\" = ?::uuid", variant);
        try {
            List<StockLedgerMismatch> found = mismatchOf(variant);
            assertThat(found).hasSize(1);
            assertThat(found.getFirst().stock()).isEqualTo(8);
            assertThat(found.getFirst().ledgerTotal()).isEqualTo(5);

            JsonNode dashboard = ok(admin.get("/admin/dashboard")).body().path("stockLedgerMismatches");
            assertThat(dashboard.findValuesAsString("variantId")).contains(variant);
        } finally {
            jdbc.update("update \"product_variants\" set \"stock\" = \"stock\" - 3 where \"id\" = ?::uuid", variant);
        }
        assertThat(mismatchOf(variant)).as("되돌린 뒤").isEmpty();
    }

    /**
     * 주문·취소·환불 재입고·조정을 거쳐도 이력 합계와 재고가 계속 같음
     */
    @Test
    void ledgerStaysConsistentAcrossOrderLifecycle() {
        ApiClient admin = admin();
        String variant = variantId(product(admin, 10));
        ApiClient member = member();
        String canceled = created(member.post("/orders", orderOf(variant, 2))).body().path("id").asString();
        String refunded = created(member.post("/orders", orderOf(variant, 3))).body().path("id").asString();

        created(member.post("/orders/" + canceled + "/cancel", null));
        ok(admin.patch("/admin/orders/" + refunded + "/status", Map.of("status", "PAID")));
        ok(admin.patch("/admin/orders/" + refunded + "/status", Map.of("status", "FULLY_REFUNDED", "restock", true)));
        created(admin.post("/admin/inventory/variants/" + variant + "/adjust", Map.of("delta", -1, "reason", "전시용")));

        assertThat(stockOf(variant)).isEqualTo(9);
        assertThat(ledgerOf(variant)).isEqualTo(9);
        assertThat(mismatchOf(variant)).isEmpty();
    }

    /**
     * 대조 결과 중 해당 옵션의 항목
     */
    private List<StockLedgerMismatch> mismatchOf(String variant) {
        return inventory.ledgerMismatches().stream().filter(item -> item.variantId().toString().equals(variant)).toList();
    }
}

package io.github.gjaku1031.damdam.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.gjaku1031.damdam.fixture.ApiClient;
import io.github.gjaku1031.damdam.fixture.IntegrationTest;
import tools.jackson.databind.JsonNode;

/**
 * 운영 설정과 방문자에게 공개하는 사이트 정보
 */
final class SystemConfigApiTest extends IntegrationTest {
    /**
     * 다른 테스트의 주문 기한에 영향을 주지 않도록 입금 기한 설정을 기본값(빈 값)으로 되돌림
     */
    @AfterEach
    void resetDueDays() {
        created(admin().post("/admin/system/configs", Map.of("key", "bank_transfer_due_days", "value", "")));
    }

    /**
     * 입금 기한 일수는 1~30의 숫자만 저장할 수 있음
     */
    @Test
    void dueDaysMustBeWithinRange() {
        ApiClient admin = admin();

        for (String invalid : new String[] {"abc", "0", "31", "2.5"}) {
            assertThat(admin.post("/admin/system/configs", Map.of("key", "bank_transfer_due_days", "value", invalid))
                    .status()).as(invalid).isEqualTo(400);
        }
        assertThat(ok(visitor().get("/site")).body().path("paymentDueDays").asInt()).as("기본값").isEqualTo(2);
    }

    /**
     * 기한 일수를 바꾸면 그 뒤의 주문에만 적용되고 이미 받은 주문의 기한은 그대로
     */
    @Test
    void changedDueDaysApplyToNewOrdersOnly() {
        ApiClient admin = admin();
        String variant = variantId(product(admin, 5));
        ApiClient member = member();
        JsonNode before = created(member.post("/orders", orderOf(variant, 1))).body();

        created(admin.post("/admin/system/configs", Map.of("key", "bank_transfer_due_days", "value", "5")));
        JsonNode after = created(member.post("/orders", orderOf(variant, 1))).body();

        assertThat(daysUntilDue(after)).isEqualTo(5);
        assertThat(daysUntilDue(ok(member.get("/orders/" + before.path("id").asString())).body())).isEqualTo(2);
        assertThat(ok(visitor().get("/site")).body().path("paymentDueDays").asInt()).isEqualTo(5);
    }

    /**
     * 운영자가 저장한 사업자 정보·입금 계좌는 로그인하지 않은 방문자의 사이트 정보에 나타남
     */
    @Test
    void savedBusinessInfoAppearsInPublicSiteInfo() {
        ApiClient admin = admin();
        String name = "담담 " + uniqueName();

        assertThat(member().post("/admin/system/configs", Map.of("key", "business_name", "value", name)).status())
                .isEqualTo(403);
        created(admin.post("/admin/system/configs", Map.of("key", "business_name", "value", name)));
        created(admin.post("/admin/system/configs", Map.of("key", "bank_transfer_account", "value", "테스트은행 000-00")));

        JsonNode site = ok(visitor().get("/site")).body();
        assertThat(site.path("businessName").asString()).isEqualTo(name);
        assertThat(site.path("bankInstructions").asString()).contains("테스트은행 000-00");
    }

    /**
     * 주문일에서 입금 기한까지의 날짜 수(한국 시간 기준)
     */
    private static long daysUntilDue(JsonNode order) {
        ZoneId seoul = ZoneId.of("Asia/Seoul");
        return Instant.parse(order.path("createdAt").asString()).atZone(seoul).toLocalDate()
                .until(Instant.parse(order.path("paymentDueAt").asString()).atZone(seoul).toLocalDate()).getDays();
    }
}

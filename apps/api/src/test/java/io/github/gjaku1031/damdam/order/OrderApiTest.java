package io.github.gjaku1031.damdam.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.gjaku1031.damdam.fixture.ApiClient;
import io.github.gjaku1031.damdam.fixture.ApiResponse;
import io.github.gjaku1031.damdam.fixture.IntegrationTest;
import io.github.gjaku1031.damdam.order.service.OrderService;
import tools.jackson.databind.JsonNode;

/**
 * 주문 생성·취소·상태 전이와 재고, 결제 대기 주문 수 제한, 입금 기한 자동 취소
 */
final class OrderApiTest extends IntegrationTest {
    /**
     * 입금 기한 자동 취소를 스케줄러 없이 직접 실행하기 위한 주문 서비스
     */
    @Autowired
    private OrderService orders;

    /**
     * 주문하면 재고가 줄고 같은 양의 재고 이력이 남으며, 금액은 서버의 가격으로 계산
     */
    @Test
    void orderDeductsStockAndRecordsLedger() {
        String variant = variantId(product(admin(), 5));

        JsonNode order = created(member().post("/orders", orderOf(variant, 2))).body();

        assertThat(order.path("status").asString()).isEqualTo("PENDING_PAYMENT");
        assertThat(order.path("totalAmount").asInt()).isEqualTo(2000);
        assertThat(order.path("items").path(0).path("quantity").asInt()).isEqualTo(2);
        assertThat(stockOf(variant)).isEqualTo(3);
        assertThat(ledgerOf(variant)).isEqualTo(3);
    }

    /**
     * 입금 기한은 주문일(한국 시간)에서 기본 2일 뒤의 23:59:59
     */
    @Test
    void paymentDeadlineIsEndOfSecondDayInSeoul() {
        JsonNode order = created(member().post("/orders", orderOf(variantId(product(admin(), 1)), 1))).body();

        ZonedDateTime due = Instant.parse(order.path("paymentDueAt").asString()).atZone(ZoneId.of("Asia/Seoul"));
        ZonedDateTime ordered = Instant.parse(order.path("createdAt").asString()).atZone(ZoneId.of("Asia/Seoul"));
        assertThat(due.toLocalDate()).isEqualTo(ordered.toLocalDate().plusDays(2));
        assertThat(due.toLocalTime().toString()).isEqualTo("23:59:59");
    }

    /**
     * 재고보다 많이 주문하면 400이고 재고와 이력은 그대로
     */
    @Test
    void rejectsOrderBeyondStock() {
        String variant = variantId(product(admin(), 1));

        ApiResponse response = member().post("/orders", orderOf(variant, 2));

        assertThat(response.status()).isEqualTo(400);
        assertThat(stockOf(variant)).isEqualTo(1);
        assertThat(ledgerOf(variant)).isEqualTo(1);
    }

    /**
     * 같은 옵션을 두 줄로 나눠 담은 주문, 숨긴 상품의 주문, 없는 옵션의 주문은 400
     */
    @Test
    void rejectsDuplicateHiddenAndUnknownOptions() {
        ApiClient admin = admin();
        String variant = variantId(product(admin, 5));
        String hidden = variantId(product(admin, 5, false));
        Map<String, Object> duplicated = new HashMap<>(orderOf(variant, 1));
        duplicated.put("items", List.of(Map.of("variantId", variant, "quantity", 1), Map.of("variantId", variant, "quantity", 1)));

        assertThat(member().post("/orders", duplicated).status()).isEqualTo(400);
        assertThat(member().post("/orders", orderOf(hidden, 1)).status()).isEqualTo(400);
        assertThat(member().post("/orders", orderOf(UUID.randomUUID().toString(), 1)).status()).isEqualTo(400);
        assertThat(stockOf(variant)).isEqualTo(5);
        assertThat(stockOf(hidden)).isEqualTo(5);
    }

    /**
     * 재고 2개에 회원 10명이 동시에 주문해도 2건만 성공하고 재고는 음수가 되지 않음
     */
    @Test
    void concurrentOrdersNeverOversell() throws Exception {
        String variant = variantId(product(admin(), 2));
        List<ApiClient> buyers = new ArrayList<>();
        for (int index = 0; index < 10; index++) buyers.add(member());

        List<Integer> statuses = inParallel(buyers.stream()
                .<Callable<Integer>>map(buyer -> () -> buyer.post("/orders", orderOf(variant, 1)).status()).toList());

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(2);
        assertThat(statuses).filteredOn(status -> status == 400).hasSize(8);
        assertThat(stockOf(variant)).isZero();
        assertThat(ledgerOf(variant)).isZero();
    }

    /**
     * 입금 전 주문은 회원당 5건까지, 6번째는 409이고 재고를 줄이지 않음
     */
    @Test
    void sixthUnpaidOrderIsRejected() {
        String variant = variantId(product(admin(), 20));
        ApiClient member = member();
        for (int index = 0; index < 5; index++) created(member.post("/orders", orderOf(variant, 1)));

        ApiResponse sixth = member.post("/orders", orderOf(variant, 1));

        assertThat(sixth.status()).isEqualTo(409);
        assertThat(sixth.message()).contains("5건");
        assertThat(stockOf(variant)).isEqualTo(15);
        assertThat(created(member().post("/orders", orderOf(variant, 1))).status()).as("다른 회원은 영향 없음").isEqualTo(201);
    }

    /**
     * 같은 회원이 동시에 12건을 주문해도 5건만 성공(회원 행 잠금으로 건수 확인을 한 줄로 세움)
     */
    @Test
    void concurrentOrdersOfOneMemberRespectTheCap() throws Exception {
        String variant = variantId(product(admin(), 30));
        ApiClient member = member();
        List<Callable<Integer>> attempts = new ArrayList<>();
        for (int index = 0; index < 12; index++) attempts.add(() -> member.post("/orders", orderOf(variant, 1)).status());

        List<Integer> statuses = inParallel(attempts);

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(5);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(7);
        assertThat(stockOf(variant)).isEqualTo(25);
    }

    /**
     * 취소하거나 입금이 확인되면 자리가 나서 다시 주문할 수 있음
     */
    @Test
    void capFreesUpAfterCancelOrPayment() {
        ApiClient admin = admin();
        String variant = variantId(product(admin, 20));
        ApiClient member = member();
        List<String> ids = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            ids.add(created(member.post("/orders", orderOf(variant, 1))).body().path("id").asString());
        }

        created(member.post("/orders/" + ids.get(0) + "/cancel", null));
        created(member.post("/orders", orderOf(variant, 1)));
        assertThat(member.post("/orders", orderOf(variant, 1)).status()).isEqualTo(409);
        ok(admin.patch("/admin/orders/" + ids.get(1) + "/status", Map.of("status", "PAID")));

        assertThat(member.post("/orders", orderOf(variant, 1)).status()).isEqualTo(201);
    }

    /**
     * 운영자가 대신 넣는 주문은 건수 제한을 받지 않고, 받은 돈으로 처리하면 바로 입금 확인 상태
     */
    @Test
    void proxyOrderIgnoresCapAndCanBePaidAtOnce() {
        ApiClient admin = admin();
        String variant = variantId(product(admin, 20));
        ApiClient member = member();
        String source = "";
        for (int index = 0; index < 5; index++) {
            source = created(member.post("/orders", orderOf(variant, 1))).body().path("id").asString();
        }

        JsonNode proxy = created(admin.post("/admin/orders", Map.of("sourceOrderId", source,
                "items", List.of(Map.of("variantId", variant, "quantity", 2)), "paid", true))).body();

        assertThat(proxy.path("status").asString()).isEqualTo("PAID");
        assertThat(proxy.path("buyerName").asString()).isEqualTo("주문자");
        assertThat(stockOf(variant)).isEqualTo(13);
        assertThat(ledgerOf(variant)).isEqualTo(13);
    }

    /**
     * 고객은 입금 전인 자기 주문만 취소할 수 있고 취소하면 재고가 돌아옴
     */
    @Test
    void customerCancelsOnlyOwnUnpaidOrder() {
        ApiClient admin = admin();
        String variant = variantId(product(admin, 4));
        ApiClient member = member();
        String unpaid = created(member.post("/orders", orderOf(variant, 2))).body().path("id").asString();
        String paid = created(member.post("/orders", orderOf(variant, 1))).body().path("id").asString();
        ok(admin.patch("/admin/orders/" + paid + "/status", Map.of("status", "PAID")));

        assertThat(member().post("/orders/" + unpaid + "/cancel", null).status()).as("다른 회원").isEqualTo(403);
        assertThat(member().get("/orders/" + unpaid).status()).as("다른 회원의 조회").isEqualTo(403);
        JsonNode canceled = created(member.post("/orders/" + unpaid + "/cancel", null)).body();
        assertThat(member.post("/orders/" + paid + "/cancel", null).status()).as("입금 확인된 주문").isEqualTo(409);
        assertThat(member.post("/orders/" + unpaid + "/cancel", null).status()).as("이미 취소한 주문").isEqualTo(409);

        assertThat(canceled.path("status").asString()).isEqualTo("CANCELED");
        assertThat(stockOf(variant)).isEqualTo(3);
        assertThat(ledgerOf(variant)).isEqualTo(3);
    }

    /**
     * 운영자는 입금 확인 → 제작·배송 → 배송 완료 순서로만 바꿀 수 있고 순서를 건너뛰면 409
     */
    @Test
    void operatorMovesOrderThroughAllowedStatusesOnly() {
        ApiClient admin = admin();
        String id = created(member().post("/orders", orderOf(variantId(product(admin, 3)), 1))).body().path("id").asString();
        String path = "/admin/orders/" + id + "/status";

        assertThat(admin.patch(path, Map.of("status", "COMPLETED")).status()).isEqualTo(409);
        ok(admin.patch(path, Map.of("status", "PAID")));
        ok(admin.patch(path, Map.of("status", "PROCESSING")));
        assertThat(admin.patch(path, Map.of("status", "CANCELED")).status()).as("발송 시작 후 취소").isEqualTo(409);
        JsonNode completed = ok(admin.patch(path, Map.of("status", "COMPLETED"))).body();

        assertThat(completed.path("status").asString()).isEqualTo("COMPLETED");
        assertThat(completed.path("paidAt").isNull()).isFalse();
        assertThat(completed.path("statusHistories")).hasSize(4);
        assertThat(completed.path("items").path(0).path("status").asString()).isEqualTo("DELIVERED");
    }

    /**
     * 전액 환불은 "재고로 되돌리기"를 고른 경우에만 재고가 돌아오고, 환불이 아닌 전이에 붙이면 400
     */
    @Test
    void refundRestocksOnlyWhenRequested() {
        ApiClient admin = admin();
        String variant = variantId(product(admin, 4));
        String restocked = paidOrder(admin, variant);
        String discarded = paidOrder(admin, variant);

        assertThat(admin.patch("/admin/orders/" + restocked + "/status", Map.of("status", "PROCESSING", "restock", true))
                .status()).isEqualTo(400);
        ok(admin.patch("/admin/orders/" + restocked + "/status", Map.of("status", "FULLY_REFUNDED", "restock", true)));
        JsonNode refunded = ok(admin.patch("/admin/orders/" + discarded + "/status", Map.of("status", "FULLY_REFUNDED")))
                .body();

        assertThat(refunded.path("status").asString()).isEqualTo("FULLY_REFUNDED");
        assertThat(stockOf(variant)).isEqualTo(3);
        assertThat(ledgerOf(variant)).isEqualTo(3);
        assertThat(admin.patch("/admin/orders/" + restocked + "/status", Map.of("status", "PAID")).status())
                .as("환불한 주문은 더 바꿀 수 없음").isEqualTo(409);
    }

    /**
     * 입금 기한이 지난 결제 대기 주문은 자동 취소되어 재고가 돌아오고, 이력에는 시스템이 취소한 것으로 남음
     */
    @Test
    void overdueUnpaidOrderIsCanceledAutomatically() {
        ApiClient admin = admin();
        String variant = variantId(product(admin, 3));
        String id = created(member().post("/orders", orderOf(variant, 2))).body().path("id").asString();
        expire(id);

        assertThat(orders.paymentOverdueOrderIds(500)).contains(UUID.fromString(id));
        assertThat(orders.cancelIfPaymentOverdue(UUID.fromString(id))).isTrue();
        assertThat(orders.cancelIfPaymentOverdue(UUID.fromString(id))).as("두 번 실행해도 한 번만 취소").isFalse();

        JsonNode order = ok(admin.get("/admin/orders/" + id)).body();
        JsonNode last = order.path("statusHistories").path(order.path("statusHistories").size() - 1);
        assertThat(order.path("status").asString()).isEqualTo("CANCELED");
        assertThat(last.path("changedBy").asString()).isEqualTo("SYSTEM");
        assertThat(stockOf(variant)).isEqualTo(3);
        assertThat(ledgerOf(variant)).isEqualTo(3);
    }

    /**
     * 기한이 남았거나 이미 입금 확인된 주문은 자동 취소 대상이 아님
     */
    @Test
    void automaticCancelSkipsOrdersInTimeOrAlreadyPaid() {
        ApiClient admin = admin();
        String variant = variantId(product(admin, 3));
        String inTime = created(member().post("/orders", orderOf(variant, 1))).body().path("id").asString();
        String paid = paidOrder(admin, variant);
        expire(paid);

        assertThat(orders.cancelIfPaymentOverdue(UUID.fromString(inTime))).isFalse();
        assertThat(orders.cancelIfPaymentOverdue(UUID.fromString(paid))).isFalse();
        assertThat(orders.paymentOverdueOrderIds(500)).doesNotContain(UUID.fromString(inTime), UUID.fromString(paid));
        assertThat(stockOf(variant)).isEqualTo(1);
    }

    /**
     * 운영자는 결제 대기 주문의 입금 기한을 지금 이후 30일 이내로만 바꿀 수 있음
     */
    @Test
    void operatorChangesDeadlineWithinLimits() {
        ApiClient admin = admin();
        String variant = variantId(product(admin, 3));
        String id = created(member().post("/orders", orderOf(variant, 1))).body().path("id").asString();
        String path = "/admin/orders/" + id + "/payment-due";
        Instant now = Instant.now();

        assertThat(admin.patch(path, Map.of("dueAt", now.minusSeconds(60).toString())).status()).isEqualTo(400);
        assertThat(admin.patch(path, Map.of("dueAt", now.plus(Duration.ofDays(31)).toString())).status()).isEqualTo(400);
        assertThat(member().patch(path, Map.of("dueAt", now.plus(Duration.ofDays(5)).toString())).status()).isEqualTo(403);
        Instant extended = now.plus(Duration.ofDays(5)).truncatedTo(ChronoUnit.SECONDS);
        JsonNode changed = ok(admin.patch(path, Map.of("dueAt", extended.toString()))).body();
        assertThat(Instant.parse(changed.path("paymentDueAt").asString())).isEqualTo(extended);

        ok(admin.patch("/admin/orders/" + id + "/status", Map.of("status", "PAID")));
        assertThat(admin.patch(path, Map.of("dueAt", extended.toString())).status()).as("입금 확인된 주문").isEqualTo(409);
    }

    /**
     * 새 회원이 주문한 뒤 운영자가 입금 확인까지 한 주문 ID
     */
    private String paidOrder(ApiClient admin, String variant) {
        String id = created(member().post("/orders", orderOf(variant, 1))).body().path("id").asString();
        ok(admin.patch("/admin/orders/" + id + "/status", Map.of("status", "PAID")));
        return id;
    }

    /**
     * 주문의 입금 기한을 한 시간 전으로 바꿔 기한이 지난 상태로 만듦
     */
    private void expire(String orderId) {
        jdbc.update("update \"orders\" set \"paymentDueAt\" = now() - interval '1 hour' where \"id\" = ?::uuid", orderId);
    }

    /**
     * 작업들을 동시에 시작해 결과를 모음
     *
     * @return 작업 순서대로의 결과
     */
    private static <T> List<T> inParallel(List<Callable<T>> tasks) throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(tasks.size())) {
            List<T> results = new ArrayList<>();
            for (Future<T> future : executor.invokeAll(tasks)) results.add(future.get());
            return results;
        }
    }
}

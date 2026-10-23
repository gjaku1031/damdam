package io.github.gjaku1031.damdam.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.gjaku1031.damdam.fixture.ApiClient;
import io.github.gjaku1031.damdam.fixture.ApiResponse;
import io.github.gjaku1031.damdam.fixture.IntegrationTest;

/**
 * 같은 회원·같은 옵션을 건드리는 주문 요청이 겹칠 때 교착 없이 모두 끝나는지 확인
 *
 * 옵션 행을 테스트 트랜잭션이 먼저 잠가 두 요청을 정해진 순서로 대기시킨 뒤 풀어 줌
 * PostgreSQL은 행 잠금 대기자를 도착 순서대로 깨우므로, 먼저 보낸 요청이 옵션을 먼저 얻음
 * 먼저 진행한 요청은 옵션을 쥔 채 INSERT의 외래 키 검사로 회원 행에 FOR KEY SHARE를 요청하고,
 * 나중 요청은 회원 행을 쥔 채 옵션을 기다림
 * 회원 행 잠금이 FOR NO KEY UPDATE라 두 요청이 서로를 기다리지 않음
 * 회원 행 잠금을 FOR UPDATE로 바꾸면 두 테스트 모두 교착으로 한 요청이 500으로 끝남
 */
final class OrderLockOrderTest extends IntegrationTest {
    /**
     * 요청이 잠금 대기에 들어가기를 기다리는 최대 시간
     */
    private static final Duration WAIT_LIMIT = Duration.ofSeconds(10);

    /**
     * 옵션 행을 잠가 둘 별도 연결을 여는 데이터 소스
     */
    @Autowired
    private DataSource dataSource;

    /**
     * 운영자 대리 주문이 옵션을 먼저 얻어도 같은 고객의 본인 주문과 교착하지 않고 둘 다 생성됨
     */
    @Test
    void proxyOrderAndCustomerOrderBothComplete() throws Exception {
        ApiClient admin = admin();
        String variant = variantId(product(admin, 10));
        ApiClient member = member();
        String source = created(member.post("/orders", orderOf(variant, 1))).body().path("id").asString();
        Map<String, Object> proxyBody = Map.of("sourceOrderId", source,
                "items", List.of(Map.of("variantId", variant, "quantity", 1)), "paid", false);

        List<ApiResponse> responses = racingBehindVariantLock(variant,
                () -> admin.post("/admin/orders", proxyBody),
                () -> member.post("/orders", orderOf(variant, 1)));

        assertThat(responses).extracting(ApiResponse::status).containsExactly(201, 201);
        assertThat(stockOf(variant)).isEqualTo(7);
        assertThat(ledgerOf(variant)).isEqualTo(7);
    }

    /**
     * 고객의 주문 취소가 옵션을 먼저 얻어도 같은 고객의 새 주문과 교착하지 않음
     *
     * 취소는 재고 이력의 작업자 외래 키로 회원 행을 요청하므로 대리 주문과 같은 순환이 생길 수 있는 경로
     */
    @Test
    void customerCancelAndNewOrderBothComplete() throws Exception {
        ApiClient admin = admin();
        String variant = variantId(product(admin, 10));
        ApiClient member = member();
        String first = created(member.post("/orders", orderOf(variant, 1))).body().path("id").asString();

        List<ApiResponse> responses = racingBehindVariantLock(variant,
                () -> member.post("/orders/" + first + "/cancel", null),
                () -> member.post("/orders", orderOf(variant, 1)));

        assertThat(responses).extracting(ApiResponse::status).containsExactly(201, 201);
        assertThat(stockOf(variant)).isEqualTo(9);
        assertThat(ledgerOf(variant)).isEqualTo(9);
    }

    /**
     * 옵션 행을 잠근 채 두 요청을 차례로 대기시킨 뒤 잠금을 풀고 결과를 모음
     *
     * 1. 별도 연결의 트랜잭션에서 옵션 행 잠금
     * 2. 첫 요청을 보내고 잠금 대기에 들어갈 때까지 기다림, 두 번째 요청도 같은 방법으로
     * 3. 잠금을 풀어 첫 요청부터 진행시킴
     *
     * @param variantId 잠가 둘 옵션 ID
     * @param first 먼저 대기시킬 요청
     * @param second 나중에 대기시킬 요청
     * @return 보낸 순서대로의 응답
     */
    private List<ApiResponse> racingBehindVariantLock(String variantId, Supplier<ApiResponse> first,
            Supplier<ApiResponse> second) throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2);
                Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try (PreparedStatement lock = blocker.prepareStatement(
                    "select 1 from \"product_variants\" where \"id\" = ?::uuid for update")) {
                lock.setString(1, variantId);
                lock.executeQuery().close();
            }
            int waiting = lockWaiters();
            Future<ApiResponse> firstResponse = executor.submit(first::get);
            awaitLockWaiters(waiting + 1);
            Future<ApiResponse> secondResponse = executor.submit(second::get);
            awaitLockWaiters(waiting + 2);
            blocker.commit();
            return List.of(firstResponse.get(30, TimeUnit.SECONDS), secondResponse.get(30, TimeUnit.SECONDS));
        }
    }

    /**
     * 잠금 대기자 수가 기대값에 이를 때까지 기다림
     *
     * @param expected 기다릴 대기자 수
     * @throws IllegalStateException {@link #WAIT_LIMIT} 안에 이르지 않을 때
     */
    private void awaitLockWaiters(int expected) throws InterruptedException {
        Instant deadline = Instant.now().plus(WAIT_LIMIT);
        while (lockWaiters() < expected) {
            if (Instant.now().isAfter(deadline)) {
                throw new IllegalStateException("요청이 잠금 대기에 들어가지 않았습니다: 기대 " + expected);
            }
            Thread.sleep(20);
        }
    }

    /**
     * 현재 DB에서 잠금을 기다리는 연결 수
     */
    private int lockWaiters() {
        return jdbc.queryForObject("select count(*) from pg_stat_activity "
                + "where datname = current_database() and wait_event_type = 'Lock'", Integer.class);
    }
}

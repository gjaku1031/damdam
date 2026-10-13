package io.github.gjaku1031.damdam.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.gjaku1031.damdam.fixture.ApiClient;
import io.github.gjaku1031.damdam.fixture.IntegrationTest;
import io.github.gjaku1031.damdam.storage.service.StorageService;
import tools.jackson.databind.JsonNode;

/**
 * 어디에도 연결되지 않은 업로드 사진의 정리
 *
 * 스케줄러가 호출하는 것과 같은 순서(대상 조회 → 티켓별 삭제)로 서비스를 직접 실행
 */
final class OrphanUploadCleanupTest extends IntegrationTest {
    /**
     * 정리 작업을 직접 실행하기 위한 저장 서비스
     */
    @Autowired
    private StorageService storage;

    /**
     * 정리와 겹치는 다른 트랜잭션을 만들기 위한 트랜잭션 관리자
     */
    @Autowired
    private PlatformTransactionManager transactions;

    /**
     * 올린 지 하루가 지났고 어디에도 붙이지 않은 사진은 원본·변환본·티켓이 모두 지워짐
     */
    @Test
    void removesOldUnattachedUploadWithItsVariants() throws IOException {
        ApiClient admin = admin();
        String key = uploadPhoto(admin, "PRODUCT");
        List<Path> variants = List.of(variant(key, "s"), variant(key, "m"), variant(key, "l"));
        for (Path variant : variants) Files.write(variant, PNG);
        age(key);

        sweep();

        assertThat(ticketExists(key)).isFalse();
        assertThat(UPLOADS.resolve(key)).doesNotExist();
        assertThat(variants).allSatisfy(variant -> assertThat(variant).doesNotExist());
        assertThat(admin.get("/storage/files/" + key).status()).isEqualTo(404);
    }

    /**
     * 방금 올린 사진은 아직 붙이지 않았어도 지우지 않음(올린 뒤 저장하기 전의 편집 중인 사진)
     */
    @Test
    void keepsRecentUploadEvenIfUnattached() {
        String key = uploadPhoto(admin(), "PRODUCT");

        sweep();

        assertKept(key);
    }

    /**
     * 공개 상품, 숨긴 상품, 삭제한 상품에 붙은 사진은 지우지 않음
     */
    @Test
    void keepsPhotosOfVisibleHiddenAndDeletedProducts() {
        ApiClient admin = admin();
        String visible = uploadPhoto(admin, "PRODUCT");
        String hidden = uploadPhoto(admin, "PRODUCT");
        String deleted = uploadPhoto(admin, "PRODUCT");
        product(admin, 1, visible);
        product(admin, 1, false, hidden);
        ok(admin.delete("/admin/products/" + product(admin, 1, deleted).path("id").asString()));
        age(visible, hidden, deleted);

        sweep();

        assertKept(visible);
        assertKept(hidden);
        assertKept(deleted);
    }

    /**
     * 상품에서 뗀 사진은 지워지지만, 그 사진이 주문 내역의 대표 사진으로 남아 있으면 지우지 않음
     */
    @Test
    void removesDetachedPhotoUnlessAnOrderStillShowsIt() {
        ApiClient admin = admin();
        String detached = uploadPhoto(admin, "PRODUCT");
        String ordered = uploadPhoto(admin, "PRODUCT");
        JsonNode plain = product(admin, 1, detached);
        JsonNode sold = product(admin, 1, ordered);
        created(member().post("/orders", orderOf(variantId(sold), 1)));
        ok(admin.patch("/admin/products/" + plain.path("id").asString(), Map.of("images", List.of())));
        ok(admin.patch("/admin/products/" + sold.path("id").asString(), Map.of("images", List.of())));
        age(detached, ordered);

        sweep();

        assertThat(ticketExists(detached)).isFalse();
        assertThat(UPLOADS.resolve(detached)).doesNotExist();
        assertKept(ordered);
    }

    /**
     * 임시글 초안에만 있는 사진과 발행한 글의 사진은 지우지 않고, 초안에서 뺀 사진은 지움
     */
    @Test
    void followsDraftAndPublishedPostContent() {
        ApiClient admin = admin();
        String inDraft = uploadPhoto(admin, "POST");
        String published = uploadPhoto(admin, "POST");
        String removed = uploadPhoto(admin, "POST");
        saveDraft(admin, newPost(admin), inDraft);
        JsonNode post = saveDraft(admin, newPost(admin), published);
        created(admin.post("/admin/posts/" + post.path("id").asString() + "/publish",
                Map.of("revision", post.path("revision").asInt())));
        saveDraft(admin, saveDraft(admin, newPost(admin), removed));
        age(inDraft, published, removed);

        sweep();

        assertKept(inDraft);
        assertKept(published);
        assertThat(ticketExists(removed)).isFalse();
        assertThat(UPLOADS.resolve(removed)).doesNotExist();
    }

    /**
     * 공지 본문에 넣은 사진은 지우지 않음
     */
    @Test
    void keepsPhotoUsedInNoticeBody() {
        ApiClient admin = admin();
        String key = uploadPhoto(admin, "POST");
        created(admin.post("/admin/cms/notices", Map.of("title", "사진 있는 공지",
                "bodyHtml", "<p>안내</p><img src=\"/api/storage/files/" + key + "\" alt=\"\">")));
        age(key);

        sweep();

        assertKept(key);
    }

    /**
     * 업로드에 실패한 티켓과 거기 남은 파일도 하루가 지나면 정리
     */
    @Test
    void removesFailedTicketAndItsStrayFile() throws IOException {
        JsonNode ticket = created(admin().post("/storage/tickets",
                Map.of("domain", "PRODUCT", "contentType", "image/png", "sizeBytes", PNG.length))).body();
        String key = ticket.path("objectKey").asString();
        Files.createDirectories(UPLOADS.resolve(key).getParent());
        Files.write(UPLOADS.resolve(key), PNG);
        jdbc.update("update \"storage_upload_tickets\" set \"status\" = 'FAILED', "
                + "\"failedAt\" = now() - interval '25 hours' where \"objectKey\" = ?", key);

        sweep();

        assertThat(ticketExists(key)).isFalse();
        assertThat(UPLOADS.resolve(key)).doesNotExist();
    }

    /**
     * 정리된 사진은 더 이상 상품에 붙일 수 없음
     */
    @Test
    void removedPhotoCanNoLongerBeAttached() {
        ApiClient admin = admin();
        String key = uploadPhoto(admin, "PRODUCT");
        JsonNode product = product(admin, 1);
        age(key);
        sweep();

        int status = admin.patch("/admin/products/" + product.path("id").asString(),
                Map.of("images", List.of(Map.of("objectKey", key)))).status();

        assertThat(status).isEqualTo(400);
    }

    /**
     * 정리와 같은 순간에 다른 트랜잭션이 사진을 붙이고 있으면, 정리는 그 트랜잭션이 끝나기를 기다렸다가 지우지 않음
     *
     * 붙이는 쪽이 쥐는 티켓의 공유 잠금과 정리하는 쪽의 배타 잠금·재확인을 검증
     */
    @Test
    void waitsForConcurrentAttachAndThenKeepsThePhoto() throws Exception {
        ApiClient admin = admin();
        String key = uploadPhoto(admin, "PRODUCT");
        String productId = product(admin, 1).path("id").asString();
        age(key);
        UUID ticketId = jdbc.queryForObject("select \"id\" from \"storage_upload_tickets\" where \"objectKey\" = ?",
                UUID.class, key);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch sweeping = new CountDownLatch(1);

        // 사진을 붙이는 트랜잭션: 티켓을 공유 잠금한 채 정리가 시작되기를 기다렸다가 상품 사진으로 저장하고 커밋
        CompletableFuture<Void> attach = CompletableFuture.runAsync(() -> new TransactionTemplate(transactions)
                .executeWithoutResult(status -> {
                    storage.assertUsable(key);
                    locked.countDown();
                    await(sweeping);
                    pause(700);
                    jdbc.update("insert into \"product_images\" (\"productId\", \"objectKey\", \"sortOrder\") "
                            + "values (?::uuid, ?, 0)", productId, key);
                }));
        await(locked);
        sweeping.countDown();
        long started = System.nanoTime();
        boolean deleted = storage.deleteIfOrphan(ticketId);
        long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        attach.get(10, TimeUnit.SECONDS);

        assertThat(deleted).isFalse();
        assertThat(waitedMillis).as("붙이는 트랜잭션이 끝날 때까지 기다림").isGreaterThanOrEqualTo(500);
        assertKept(key);
    }

    /**
     * 스케줄러와 같은 방식으로 정리 한 번 실행
     */
    private void sweep() {
        storage.orphanTicketIds(10_000).forEach(storage::deleteIfOrphan);
    }

    /**
     * 업로드한 지 25시간이 지난 것처럼 티켓의 검증 시각을 옮김
     */
    private void age(String... keys) {
        for (String key : keys) {
            jdbc.update("update \"storage_upload_tickets\" set \"verifiedAt\" = now() - interval '25 hours' "
                    + "where \"objectKey\" = ?", key);
        }
    }

    /**
     * 티켓과 원본 파일이 모두 남아 있는지 확인
     */
    private void assertKept(String key) {
        assertThat(ticketExists(key)).as("티켓 %s", key).isTrue();
        assertThat(UPLOADS.resolve(key)).exists();
    }

    /**
     * 저장 키의 업로드 티켓 존재 여부
     */
    private boolean ticketExists(String key) {
        return jdbc.queryForObject("select count(*) from \"storage_upload_tickets\" where \"objectKey\" = ?",
                Integer.class, key) > 0;
    }

    /**
     * 업로드 사진의 크기별 변환본 파일 경로
     *
     * @param code 크기 코드({@code s}·{@code m}·{@code l})
     */
    private static Path variant(String key, String code) {
        return UPLOADS.resolve(key.substring(0, key.lastIndexOf('.')) + "-" + code + ".webp");
    }

    /**
     * 새 임시글 생성
     *
     * @return 초안 응답
     */
    private JsonNode newPost(ApiClient admin) {
        return created(admin.post("/admin/posts", null)).body();
    }

    /**
     * 주어진 사진들을 본문에 넣어 초안 저장
     *
     * @param post 저장할 글의 현재 초안 응답(ID와 리비전 사용)
     * @param keys 본문에 넣을 사진 키, 없으면 사진 없는 본문
     * @return 저장 후 초안 응답
     */
    private JsonNode saveDraft(ApiClient admin, JsonNode post, String... keys) {
        StringBuilder nodes = new StringBuilder("{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"본문\"}]}");
        for (String key : keys) {
            nodes.append(",{\"type\":\"image\",\"attrs\":{\"src\":\"/api/storage/files/").append(key)
                    .append("\",\"objectKey\":\"").append(key)
                    .append("\",\"width\":100,\"align\":\"center\",\"caption\":\"\"}}");
        }
        return ok(admin.patch("/admin/posts/" + post.path("id").asString() + "/draft", Map.of(
                "revision", post.path("revision").asInt(), "title", "정리 검증 글",
                "documentJson", json.readTree("{\"type\":\"doc\",\"content\":[" + nodes + "]}"),
                "tags", List.of()))).body();
    }

    /**
     * 신호를 최대 10초 기다림
     */
    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    /**
     * 지정한 시간만큼 멈춤
     */
    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}

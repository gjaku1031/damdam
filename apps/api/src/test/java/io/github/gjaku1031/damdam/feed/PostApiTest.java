package io.github.gjaku1031.damdam.feed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.gjaku1031.damdam.fixture.ApiClient;
import io.github.gjaku1031.damdam.fixture.ApiResponse;
import io.github.gjaku1031.damdam.fixture.IntegrationTest;
import tools.jackson.databind.JsonNode;

/**
 * 게시글 초안·발행·비공개·삭제와 댓글
 */
final class PostApiTest extends IntegrationTest {
    /**
     * 임시글은 발행하기 전까지 방문자에게 보이지 않고, 발행하면 제목과 렌더링한 본문이 공개됨
     */
    @Test
    void draftBecomesPublicOnlyAfterPublish() {
        ApiClient admin = admin();
        JsonNode post = saveDraft(admin, newPost(admin), "첫 글", "공개될 본문");
        String id = post.path("id").asString();

        assertThat(visitor().get("/posts/" + id).status()).isEqualTo(404);
        created(admin.post("/admin/posts/" + id + "/publish", Map.of("revision", post.path("revision").asInt())));

        JsonNode published = ok(visitor().get("/posts/" + id)).body();
        assertThat(published.path("title").asString()).isEqualTo("첫 글");
        assertThat(published.path("bodyHtml").asString()).contains("<p>공개될 본문</p>");
        assertThat(published.has("revision") && !published.path("revision").isNull()).as("운영 정보는 공개하지 않음").isFalse();
    }

    /**
     * 발행한 글의 초안을 고쳐도 다시 발행하기 전까지 독자는 마지막 발행본을 봄
     */
    @Test
    void editingDraftDoesNotChangePublishedVersionUntilRepublished() {
        ApiClient admin = admin();
        String id = publish(admin, "원래 제목", "원래 본문");
        JsonNode current = ok(admin.get("/admin/posts/" + id + "/draft")).body();

        JsonNode edited = saveDraft(admin, current, "바꾼 제목", "바꾼 본문");
        assertThat(ok(visitor().get("/posts/" + id)).body().path("title").asString()).isEqualTo("원래 제목");
        created(admin.post("/admin/posts/" + id + "/publish", Map.of("revision", edited.path("revision").asInt())));

        JsonNode republished = ok(visitor().get("/posts/" + id)).body();
        assertThat(republished.path("title").asString()).isEqualTo("바꾼 제목");
        assertThat(republished.path("bodyHtml").asString()).contains("바꾼 본문").doesNotContain("원래 본문");
    }

    /**
     * 다른 화면에서 먼저 저장해 리비전이 달라졌으면 저장·발행 모두 409이고 먼저 저장한 내용이 남음
     */
    @Test
    void staleRevisionIsRejected() {
        ApiClient admin = admin();
        JsonNode created = newPost(admin);
        saveDraft(admin, created, "먼저 저장", "먼저 저장한 본문");
        String id = created.path("id").asString();
        int stale = created.path("revision").asInt();

        ApiResponse save = admin.patch("/admin/posts/" + id + "/draft", draftBody(stale, "늦게 저장", "늦은 본문"));
        ApiResponse publish = admin.post("/admin/posts/" + id + "/publish", Map.of("revision", stale));

        assertThat(save.status()).isEqualTo(409);
        assertThat(publish.status()).isEqualTo(409);
        assertThat(ok(admin.get("/admin/posts/" + id + "/draft")).body().path("draft").path("title").asString())
                .isEqualTo("먼저 저장");
    }

    /**
     * 제목 없는 글은 발행할 수 없음
     */
    @Test
    void cannotPublishWithoutTitle() {
        ApiClient admin = admin();
        JsonNode post = saveDraft(admin, newPost(admin), " ", "본문만 있는 글");

        int status = admin.post("/admin/posts/" + post.path("id").asString() + "/publish",
                Map.of("revision", post.path("revision").asInt())).status();

        assertThat(status).isEqualTo(400);
    }

    /**
     * 글자로 입력한 태그는 발행본에서 실행되지 않는 글자로 남음
     */
    @Test
    void scriptTypedAsTextIsEscapedInPublishedBody() {
        ApiClient admin = admin();

        String id = publish(admin, "스크립트 검증", "<script>alert(1)</script>");

        String html = ok(visitor().get("/posts/" + id)).body().path("bodyHtml").asString();
        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    /**
     * 비공개로 돌리거나 삭제한 글은 방문자에게 404
     */
    @Test
    void hiddenOrDeletedPostIsNotFoundForVisitors() {
        ApiClient admin = admin();
        String hidden = publish(admin, "숨길 글", "본문");
        String deleted = publish(admin, "지울 글", "본문");
        int revision = ok(admin.get("/admin/posts/" + hidden + "/draft")).body().path("revision").asInt();

        created(admin.post("/admin/posts/" + hidden + "/hide", Map.of("revision", revision)));
        ok(admin.delete("/admin/posts/" + deleted));

        assertThat(visitor().get("/posts/" + hidden).status()).isEqualTo(404);
        assertThat(visitor().get("/posts/" + deleted).status()).isEqualTo(404);
        assertThat(admin.get("/admin/posts/" + hidden + "/draft").status()).as("운영자는 숨긴 글을 계속 편집").isEqualTo(200);
        assertThat(admin.get("/admin/posts/" + deleted + "/draft").status()).isEqualTo(404);
    }

    /**
     * 댓글은 로그인한 회원만 쓰고 자기 댓글만 지울 수 있으며, 운영자는 어떤 댓글이든 지울 수 있음
     */
    @Test
    void commentsBelongToTheirAuthors() {
        ApiClient admin = admin();
        String id = publish(admin, "댓글 검증", "본문");
        ApiClient author = member();
        String comments = "/posts/" + id + "/comments";

        assertThat(visitor().post(comments, Map.of("content", "비로그인 댓글")).status()).isEqualTo(401);
        assertThat(author.post(comments, Map.of("content", "   ")).status()).isEqualTo(400);
        String mine = created(author.post(comments, Map.of("content", "첫 댓글"))).body().path("id").asString();
        String moderated = created(author.post(comments, Map.of("content", "운영자가 지울 댓글"))).body().path("id").asString();

        assertThat(member().delete(comments + "/" + mine).status()).as("다른 회원").isEqualTo(403);
        ok(author.delete(comments + "/" + mine));
        ok(admin.delete("/admin/posts/" + id + "/comments/" + moderated));
        assertThat(ok(visitor().get(comments)).body().toString()).doesNotContain("첫 댓글").doesNotContain("운영자가 지울 댓글");
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
     * 제목과 한 문단 본문으로 초안 저장
     *
     * @param post 저장할 글의 현재 초안 응답(ID와 리비전 사용)
     * @return 저장 후 초안 응답
     */
    private JsonNode saveDraft(ApiClient admin, JsonNode post, String title, String text) {
        return ok(admin.patch("/admin/posts/" + post.path("id").asString() + "/draft",
                draftBody(post.path("revision").asInt(), title, text))).body();
    }

    /**
     * 새 글을 써서 발행
     *
     * @return 게시글 ID
     */
    private String publish(ApiClient admin, String title, String text) {
        JsonNode post = saveDraft(admin, newPost(admin), title, text);
        created(admin.post("/admin/posts/" + post.path("id").asString() + "/publish",
                Map.of("revision", post.path("revision").asInt())));
        return post.path("id").asString();
    }

    /**
     * 초안 저장 요청 본문
     */
    private Map<String, Object> draftBody(int revision, String title, String text) {
        return Map.of("revision", revision, "title", title, "tags", List.of(), "documentJson", Map.of("type", "doc",
                "content", List.of(Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", text))))));
    }
}

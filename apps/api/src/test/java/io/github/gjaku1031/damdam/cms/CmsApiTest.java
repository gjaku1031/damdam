package io.github.gjaku1031.damdam.cms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.gjaku1031.damdam.fixture.ApiClient;
import io.github.gjaku1031.damdam.fixture.IntegrationTest;
import tools.jackson.databind.JsonNode;

/**
 * 공지·FAQ의 본문 정제와 공개 여부
 */
final class CmsApiTest extends IntegrationTest {
    /**
     * 저장하는 본문에서 스크립트와 이벤트 처리기를 제거하고, 수정할 때도 다시 정제
     */
    @Test
    void bodyIsSanitizedOnCreateAndUpdate() {
        ApiClient admin = admin();

        JsonNode created = created(admin.post("/admin/cms/notices", Map.of("title", "정제 검증",
                "bodyHtml", "<p onclick=\"steal()\">안내</p><script>alert(1)</script>"))).body();
        JsonNode updated = ok(admin.patch("/admin/cms/notices/" + created.path("id").asString(),
                Map.of("title", "정제 검증", "visible", true,
                        "bodyHtml", "<p>수정</p><a href=\"javascript:alert(1)\">링크</a>"))).body();

        assertThat(created.path("bodyHtml").asString()).isEqualTo("<p>안내</p>");
        assertThat(updated.path("bodyHtml").asString()).contains("<p>수정</p>").contains("링크").doesNotContain("javascript");
        assertThat(ok(visitor().get("/notices/" + created.path("id").asString())).body().path("bodyHtml").asString())
                .doesNotContain("javascript");
    }

    /**
     * 숨긴 공지와 삭제한 공지는 방문자에게 404이고 목록에서도 빠짐
     */
    @Test
    void hiddenOrDeletedNoticeIsNotPublic() {
        ApiClient admin = admin();
        String open = notice(admin, "보이는 공지");
        String hidden = notice(admin, "숨길 공지");
        String deleted = notice(admin, "지울 공지");

        created(admin.post("/admin/cms/notices/" + hidden + "/hide", null));
        ok(admin.delete("/admin/cms/notices/" + deleted));

        assertThat(visitor().get("/notices/" + open).status()).isEqualTo(200);
        assertThat(visitor().get("/notices/" + hidden).status()).isEqualTo(404);
        assertThat(visitor().get("/notices/" + deleted).status()).isEqualTo(404);
        assertThat(ok(visitor().get("/notices?size=100")).body().path("items").findValuesAsString("id"))
                .contains(open).doesNotContain(hidden, deleted);
        assertThat(admin.get("/admin/cms/notices/" + hidden).status()).isEqualTo(200);
    }

    /**
     * 공지와 FAQ는 목록이 따로라, 공지가 FAQ 목록에 섞이지 않음
     */
    @Test
    void noticesAndFaqsAreListedSeparately() {
        ApiClient admin = admin();
        String notice = notice(admin, "목록 검증 공지");
        String faq = created(admin.post("/admin/cms/faqs", Map.of("title", "목록 검증 질문", "bodyHtml", "<p>답</p>"))).body()
                .path("id").asString();

        assertThat(ok(visitor().get("/notices?size=100")).body().path("items").findValuesAsString("id"))
                .contains(notice).doesNotContain(faq);
        assertThat(ok(visitor().get("/faqs?size=100")).body().path("items").findValuesAsString("id"))
                .contains(faq).doesNotContain(notice);
    }

    /**
     * 공지 등록
     *
     * @return 공지 ID
     */
    private String notice(ApiClient admin, String title) {
        return created(admin.post("/admin/cms/notices", Map.of("title", title, "bodyHtml", "<p>내용</p>"))).body()
                .path("id").asString();
    }
}

package io.github.gjaku1031.damdam.feed.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import io.github.gjaku1031.damdam.common.error.ApiException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 게시글 문서(JSON)의 검증·사진 키 수집·HTML 렌더링
 */
final class PostDocumentsTest {
    /**
     * 업로드 사진 키 예시
     */
    private static final String KEY = "post/6c40f107-0254-4e7e-97a5-dc2c2788c3c0.png";

    /**
     * 문서 JSON 해석기
     */
    private final ObjectMapper json = new ObjectMapper();

    /**
     * 문단·제목·서식은 대응하는 HTML 요소로 렌더링
     */
    @Test
    void rendersParagraphsHeadingsAndMarks() {
        String html = PostDocuments.render(document("""
                {"type":"heading","attrs":{"level":2},"content":[{"type":"text","text":"제목"}]},
                {"type":"paragraph","content":[{"type":"text","text":"굵게","marks":[{"type":"bold"}]},
                  {"type":"text","text":" 보통"}]}"""));

        assertThat(html).contains("<h2>제목</h2>").contains("<strong>굵게</strong>").contains(" 보통");
    }

    /**
     * 글자로 넣은 HTML은 태그로 해석하지 않고 글자 그대로 보임
     */
    @Test
    void escapesMarkupInText() {
        String html = PostDocuments.render(document("""
                {"type":"paragraph","content":[{"type":"text","text":"<script>alert(1)</script>"}]}"""));

        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    /**
     * 사진 노드는 저장 키 기준의 파일 주소로 렌더링하고, 키는 등장 순서대로 중복 없이 수집
     */
    @Test
    void rendersImagesAndCollectsKeysInOrder() {
        JsonNode document = document("""
                {"type":"image","attrs":{"src":"/api/storage/files/%s","objectKey":"%s","width":50,"align":"center","caption":""}},
                {"type":"image","attrs":{"src":"/images/chair.jpg","width":100,"align":"center","caption":""}},
                {"type":"image","attrs":{"src":"/api/storage/files/%s","width":100,"align":"center","caption":""}}"""
                .formatted(KEY, KEY, KEY));

        assertThat(PostDocuments.imageKeys(document)).containsExactly(KEY, "/images/chair.jpg");
        assertThat(PostDocuments.render(document)).contains("src=\"/api/storage/files/" + KEY + "\"")
                .contains("src=\"/images/chair.jpg\"");
    }

    /**
     * 업로드 사진도 기존 정적 사진도 아닌 주소의 사진은 400
     */
    @Test
    void rejectsImagesFromOtherSources() {
        JsonNode external = document("""
                {"type":"image","attrs":{"src":"https://evil.example/track.png","width":100,"align":"center","caption":""}}""");
        JsonNode traversal = document("""
                {"type":"image","attrs":{"src":"/images/../../etc/passwd.png","width":100,"align":"center","caption":""}}""");

        assertBadRequest(() -> PostDocuments.imageKeys(external));
        assertBadRequest(() -> PostDocuments.imageKeys(traversal));
    }

    /**
     * 사진은 글 하나에 20장까지
     */
    @Test
    void rejectsMoreThanTwentyImages() {
        String image = "{\"type\":\"image\",\"attrs\":{\"src\":\"/images/a%d.jpg\",\"width\":100,\"align\":\"center\",\"caption\":\"\"}}";
        StringBuilder nodes = new StringBuilder();
        for (int index = 0; index < 21; index++) nodes.append(index == 0 ? "" : ",").append(image.formatted(index));

        assertBadRequest(() -> PostDocuments.imageKeys(document(nodes.toString())));
    }

    /**
     * 문서가 아니거나 알 수 없는 노드가 있으면 400
     */
    @Test
    void rejectsMalformedDocuments() {
        assertBadRequest(() -> PostDocuments.render(null));
        assertBadRequest(() -> PostDocuments.render(json.readTree("{\"type\":\"paragraph\"}")));
        assertBadRequest(() -> PostDocuments.render(document("{\"type\":\"iframe\"}")));
    }

    /**
     * 본문 노드들을 감싼 문서 생성
     *
     * @param nodes 쉼표로 이은 노드 JSON
     */
    private JsonNode document(String nodes) {
        return json.readTree("{\"type\":\"doc\",\"content\":[" + nodes + "]}");
    }

    /**
     * 실행 결과가 400 업무 예외인지 확인
     */
    private static void assertBadRequest(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}

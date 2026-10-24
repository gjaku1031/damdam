package io.github.gjaku1031.damdam.feed.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 저장하는 본문 HTML의 정제
 */
final class HtmlSanitizerTest {
    /**
     * 스크립트와 스타일 요소는 내용까지 버림
     */
    @Test
    void dropsScriptAndStyleWithTheirContent() {
        String cleaned = HtmlSanitizer.clean("<p>안녕</p><script>alert(1)</script><style>p{color:red}</style>");

        assertThat(cleaned).contains("<p>안녕</p>").doesNotContain("script").doesNotContain("alert").doesNotContain("color");
    }

    /**
     * 이벤트 처리기 속성과 허용하지 않은 속성은 지움
     */
    @Test
    void stripsEventHandlersAndUnknownAttributes() {
        String cleaned = HtmlSanitizer.clean("<p onclick=\"steal()\" class=\"x\" id=\"y\">글</p>"
                + "<img src=\"/api/storage/files/post/6c40f107-0254-4e7e-97a5-dc2c2788c3c0.jpg\" onerror=\"steal()\" alt=\"사진\">");

        assertThat(cleaned).doesNotContain("onclick").doesNotContain("onerror").doesNotContain("steal")
                .doesNotContain("class=").doesNotContain("id=");
        assertThat(cleaned).contains("src=\"/api/storage/files/post/6c40f107-0254-4e7e-97a5-dc2c2788c3c0.jpg\"").contains("alt=\"사진\"");
    }

    /**
     * 스크립트를 실행하는 주소는 공백·대소문자로 숨겨도 지우고, http·https·mailto와 상대 주소는 유지
     */
    @Test
    void removesScriptUrlsButKeepsWebLinks() {
        String cleaned = HtmlSanitizer.clean("<a href=\"javascript:alert(1)\">가</a>"
                + "<a href=\" JaVa\tScript:alert(1)\">나</a>"
                + "<a href=\"data:text/html,<b>x</b>\">다</a>"
                + "<a href=\"https://example.com/a\">라</a>"
                + "<a href=\"mailto:hello@example.com\">마</a>"
                + "<a href=\"/notices\">바</a>");

        assertThat(cleaned.toLowerCase()).doesNotContain("javascript").doesNotContain("data:");
        assertThat(cleaned).contains("href=\"https://example.com/a\"").contains("href=\"mailto:hello@example.com\"")
                .contains("href=\"/notices\"");
        assertThat(cleaned).contains("가").contains("나").contains("다");
    }

    /**
     * 허용하지 않은 요소는 껍데기만 벗기고 안의 글은 남김
     */
    @Test
    void unwrapsUnknownElementsKeepingText() {
        String cleaned = HtmlSanitizer.clean("<div><span>남는 글</span><iframe src=\"https://evil.example\"></iframe></div>");

        assertThat(cleaned).contains("남는 글").doesNotContain("<div").doesNotContain("<span").doesNotContain("iframe");
    }

    /**
     * 스타일은 정렬·정해진 너비처럼 허용한 선언만 남김
     */
    @Test
    void keepsOnlyAllowedStyleDeclarations() {
        String cleaned = HtmlSanitizer.clean("<p style=\"text-align:center; position:fixed; background:url(x)\">가운데</p>"
                + "<img src=\"/api/storage/files/post/6c40f107-0254-4e7e-97a5-dc2c2788c3c0.jpg\" style=\"width:50%; width:33%; height:auto\">");

        assertThat(cleaned).contains("text-align").contains("center").doesNotContain("position").doesNotContain("url(");
        assertThat(cleaned).contains("50%").doesNotContain("33%");
    }

    /**
     * 허용한 서식 요소는 그대로 유지
     */
    @Test
    void keepsAllowedFormatting() {
        String html = "<h2>제목</h2><p><strong>굵게</strong> <em>기울임</em></p><ul><li>항목</li></ul><blockquote>인용</blockquote>";

        assertThat(HtmlSanitizer.clean(html)).isEqualTo(html);
    }
}

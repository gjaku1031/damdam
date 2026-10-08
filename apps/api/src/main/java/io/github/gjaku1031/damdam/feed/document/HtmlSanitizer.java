package io.github.gjaku1031.damdam.feed.document;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/**
 * 게시글·공지 본문 HTML 정제
 *
 * 허용 태그·속성·스타일 값만 남기는 화이트리스트 방식
 * 허용하지 않은 태그는 태그만 제거하고 텍스트는 유지, {@code script}·{@code style} 등은 내용까지 제거
 */
public final class HtmlSanitizer {
    /**
     * 허용 태그
     */
    private static final Set<String> ALLOWED_TAGS = Set.of("p", "br", "h1", "h2", "h3", "strong", "b", "em", "i",
            "u", "s", "del", "ul", "ol", "li", "blockquote", "pre", "code", "a", "hr", "figure", "figcaption", "img");

    /**
     * 내용까지 제거할 태그
     */
    private static final Set<String> DISCARDED_WITH_CONTENT = Set.of("script", "style", "textarea", "option",
            "noscript");

    /**
     * 태그별 허용 속성
     */
    private static final Map<String, Set<String>> ALLOWED_ATTRIBUTES = Map.of(
            "a", Set.of("href", "target", "rel"),
            "img", Set.of("src", "alt", "style"),
            "p", Set.of("style"),
            "h1", Set.of("style"),
            "h2", Set.of("style"),
            "h3", Set.of("style"),
            "figure", Set.of("style"));

    /**
     * 허용 스타일 속성과 값 형식
     */
    private static final Map<String, Pattern> ALLOWED_STYLES = Map.of(
            "text-align", Pattern.compile("^(left|center|right)$"),
            "width", Pattern.compile("^(25|50|75|100)%$"),
            "height", Pattern.compile("^auto$"),
            "margin-left", Pattern.compile("^(0|0px|auto)$"),
            "margin-right", Pattern.compile("^(0|0px|auto)$"));

    /**
     * 주소의 스킴 부분
     */
    private static final Pattern URL_SCHEME = Pattern.compile("^([a-zA-Z][a-zA-Z0-9.\\-+]*):");

    /**
     * 링크·사진 주소에 허용하는 스킴, 스킴 없는 상대 주소는 허용
     */
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https", "mailto");

    /**
     * 주소 검사 전에 제거할 제어 문자·공백
     */
    private static final Pattern URL_NOISE = Pattern.compile("[\\x00-\\x20]+|<!--.*?-->");

    /**
     * 유틸리티 클래스 생성 방지
     */
    private HtmlSanitizer() {}

    /**
     * HTML 정제
     *
     * @param html 정제할 HTML 조각, {@code null}이면 빈 문자열로 취급
     * @return 허용 요소만 남긴 HTML, 링크에는 {@code target="_blank" rel="noopener noreferrer"} 지정
     */
    public static String clean(String html) {
        Document source = Jsoup.parseBodyFragment(html == null ? "" : html);
        Document target = Document.createShell("");
        target.outputSettings().prettyPrint(false);
        copyChildren(source.body(), target.body());
        return target.body().html();
    }

    /**
     * 허용된 자식 노드를 대상 요소로 복사
     *
     * 주석·스크립트 데이터 노드는 복사하지 않음
     */
    private static void copyChildren(Element from, Element to) {
        for (Node child : from.childNodes()) {
            if (child instanceof TextNode text) {
                to.appendChild(new TextNode(text.getWholeText()));
            } else if (child instanceof Element element) {
                String tag = element.normalName();
                if (DISCARDED_WITH_CONTENT.contains(tag)) continue;
                // 허용하지 않은 태그는 벗기고 내용만 이어 붙임
                if (!ALLOWED_TAGS.contains(tag)) {
                    copyChildren(element, to);
                    continue;
                }
                Element copy = to.appendElement(tag);
                copyAttributes(element, copy);
                if ("a".equals(tag)) copy.attr("rel", "noopener noreferrer").attr("target", "_blank");
                copyChildren(element, copy);
            }
        }
    }

    /**
     * 허용 속성 복사
     *
     * 주소 속성은 스킴 검사, {@code style}은 허용 선언만 남겨 {@code 속성:값;속성:값} 형식으로 재작성
     */
    private static void copyAttributes(Element from, Element to) {
        Set<String> allowed = ALLOWED_ATTRIBUTES.getOrDefault(to.normalName(), Set.of());
        for (Attribute attribute : from.attributes()) {
            String name = attribute.getKey().toLowerCase(Locale.ROOT);
            String value = attribute.getValue();
            if (!allowed.contains(name)) continue;
            if (("href".equals(name) || "src".equals(name)) && !isAllowedUrl(value)) continue;
            if ("style".equals(name)) {
                value = filterStyle(value);
                if (value.isEmpty()) continue;
            }
            to.attr(name, value);
        }
    }

    /**
     * 주소 스킴 허용 여부
     *
     * @return 상대 주소·프로토콜 상대 주소·허용 스킴이면 {@code true}
     */
    private static boolean isAllowedUrl(String url) {
        String normalized = URL_NOISE.matcher(url).replaceAll("");
        if (normalized.startsWith("//")) return true;
        Matcher scheme = URL_SCHEME.matcher(normalized);
        return !scheme.find() || ALLOWED_SCHEMES.contains(scheme.group(1).toLowerCase(Locale.ROOT));
    }

    /**
     * 허용 스타일 선언만 남김
     *
     * @return 남은 선언, 없으면 빈 문자열
     */
    private static String filterStyle(String style) {
        List<String> kept = new ArrayList<>();
        for (String declaration : style.split(";")) {
            int colon = declaration.indexOf(':');
            if (colon < 0) continue;
            String property = declaration.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = declaration.substring(colon + 1).trim();
            Pattern pattern = ALLOWED_STYLES.get(property);
            if (pattern != null && pattern.matcher(value).matches()) kept.add(property + ":" + value);
        }
        return String.join(";", kept);
    }
}

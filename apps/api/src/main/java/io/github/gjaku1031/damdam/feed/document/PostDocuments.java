package io.github.gjaku1031.damdam.feed.document;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

import io.github.gjaku1031.damdam.common.error.ApiException;
import io.github.gjaku1031.damdam.storage.service.ImageKeys;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 블로그 본문(TipTap 문서 JSON) 검증·HTML 렌더링
 *
 * 웹 에디터(TipTap StarterKit, 사진, 문단 정렬)와 같은 노드·마크만 허용
 * 렌더링 결과는 {@link HtmlSanitizer}로 한 번 더 정제
 */
public final class PostDocuments {
    /**
     * 허용 노드 종류
     */
    private static final Set<String> NODE_TYPES = Set.of("doc", "paragraph", "text", "heading", "bulletList",
            "orderedList", "listItem", "blockquote", "codeBlock", "hardBreak", "horizontalRule", "image");

    /**
     * 허용 마크 종류
     */
    private static final Set<String> MARK_TYPES = Set.of("bold", "italic", "underline", "strike", "code", "link");

    /**
     * 허용 사진 너비(%)
     */
    private static final Set<Double> IMAGE_WIDTHS = Set.of(25d, 50d, 75d, 100d);

    /**
     * 최대 중첩 깊이
     */
    private static final int MAX_DEPTH = 100;

    /**
     * 최대 노드 수
     */
    private static final int MAX_NODES = 10_000;

    /**
     * 본문 사진 최대 장수
     */
    private static final int MAX_IMAGES = 20;

    /**
     * 직렬화한 문서의 최대 길이(문자)
     */
    private static final int MAX_JSON_LENGTH = 500_000;

    /**
     * 노드 생성기
     */
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    /**
     * 유틸리티 클래스 생성 방지
     */
    private PostDocuments() {}

    /**
     * 문서 검증과 HTML 렌더링
     *
     * 1. 노드·마크 종류, 깊이, 노드 수 검증
     * 2. 사진 노드의 {@code src}·{@code objectKey}를 저장 키 기준으로 다시 쓰고 너비 검증(입력 문서를 직접 수정)
     * 3. 사진 장수·문서 크기 검증 후 HTML 생성과 정제
     *
     * @param document 검증할 문서, 사진 속성이 정규화됨
     * @return 정제된 HTML
     * @throws ApiException 형식·크기·사진 규칙을 벗어났을 때(400)
     */
    public static String render(JsonNode document) {
        if (document == null || !document.isObject() || !"doc".equals(text(document.get("type")))) {
            throw ApiException.badRequest("본문 형식 또는 크기를 확인해주세요.");
        }
        validate(document, 0, new int[] {0});
        imageKeys(document);
        if (document.toString().length() > MAX_JSON_LENGTH) throw ApiException.badRequest("본문이 너무 깁니다.");

        // TipTap generateHTML과 같은 구조로 만든 뒤 허용 요소만 남김
        Document html = Document.createShell("");
        html.outputSettings().prettyPrint(false);
        Element body = html.body();
        try {
            appendNode(body, document);
        } catch (IllegalArgumentException invalid) {
            throw ApiException.badRequest("본문 형식을 확인해주세요.");
        }
        return HtmlSanitizer.clean(body.html());
    }

    /**
     * 문서의 사진 저장 키 목록
     *
     * {@code objectKey}가 없으면 {@code src}의 파일 API 주소나 기존 정적 사진 주소에서 키를 구함
     *
     * @return 등장 순서의 중복 없는 키
     * @throws ApiException 업로드·기존 정적 사진이 아닌 주소가 있거나 20장을 넘을 때(400)
     */
    public static List<String> imageKeys(JsonNode document) {
        List<String> keys = new ArrayList<>();
        collectImageKeys(document, keys);
        if (keys.size() > MAX_IMAGES) throw ApiException.badRequest("사진은 최대 20장입니다.");
        return List.copyOf(new LinkedHashSet<>(keys));
    }

    /**
     * 사진 노드 생성
     *
     * @param key 저장 키
     * @return 가운데 정렬·100% 너비·설명 없는 사진 노드
     */
    public static ObjectNode imageNode(String key) {
        ObjectNode attrs = NODES.objectNode()
                .put("src", ImageKeys.publicUrl(key))
                .put("objectKey", key)
                .put("width", 100)
                .put("align", "center")
                .put("caption", "");
        ObjectNode image = NODES.objectNode().put("type", "image");
        image.set("attrs", attrs);
        return image;
    }

    /**
     * 노드 구조 재귀 검증과 사진 속성 정규화
     *
     * @param count 지금까지 방문한 노드 수(배열 하나로 공유)
     * @throws ApiException 지원하지 않는 형식이거나 사진 크기가 잘못됐을 때(400)
     */
    private static void validate(JsonNode node, int depth, int[] count) {
        boolean invalid = node == null || !node.isObject()
                || node.has("content") && !node.get("content").isArray()
                || node.has("marks") && !node.get("marks").isArray()
                || depth > MAX_DEPTH || ++count[0] > MAX_NODES
                || !NODE_TYPES.contains(text(node.get("type")));
        if (!invalid && node.has("marks")) {
            for (JsonNode mark : node.get("marks")) {
                invalid |= mark == null || !mark.isObject() || !MARK_TYPES.contains(text(mark.get("type")));
            }
        }
        if (invalid) throw ApiException.badRequest("지원하지 않는 본문 형식입니다.");

        // 사진은 저장 키 기준으로 주소를 다시 쓰고 허용 너비만 받음
        if ("image".equals(text(node.get("type")))) {
            String key = imageKeys(node).getFirst();
            ObjectNode attrs = node.get("attrs") instanceof ObjectNode existing ? existing : NODES.objectNode();
            attrs.put("src", ImageKeys.publicUrl(key)).put("objectKey", key);
            ((ObjectNode) node).set("attrs", attrs);
            if (!IMAGE_WIDTHS.contains(number(attrs.get("width"), 100))) {
                throw ApiException.badRequest("사진 크기를 확인해주세요.");
            }
        }
        if (node.has("content")) {
            for (JsonNode child : node.get("content")) validate(child, depth + 1, count);
        }
    }

    /**
     * 사진 키 재귀 수집
     *
     * @throws ApiException 허용되지 않는 사진 주소일 때(400)
     */
    private static void collectImageKeys(JsonNode node, List<String> keys) {
        if (node == null || !node.isObject()) return;
        if ("image".equals(text(node.get("type")))) {
            JsonNode attrs = node.get("attrs");
            String src = attrs == null ? "" : jsString(attrs.get("src"), "");
            String objectKey = attrs == null ? "" : jsString(attrs.get("objectKey"), "");
            String key = !objectKey.isEmpty() ? objectKey
                    : src.startsWith(ImageKeys.FILE_URL_PREFIX) ? src.substring(ImageKeys.FILE_URL_PREFIX.length()) : src;
            if (!ImageKeys.ATTACHABLE.matcher(key).matches() || key.contains("..")) {
                throw ApiException.badRequest("업로드한 사진만 본문에 넣을 수 있습니다.");
            }
            keys.add(key);
        }
        JsonNode content = node.get("content");
        if (content != null && content.isArray()) {
            for (JsonNode child : content) collectImageKeys(child, keys);
        }
    }

    /**
     * 노드를 HTML 요소로 추가
     *
     * @throws IllegalArgumentException 빈 텍스트 노드처럼 문서로 만들 수 없을 때
     */
    private static void appendNode(Element parent, JsonNode node) {
        JsonNode attrs = node.get("attrs");
        switch (text(node.get("type"))) {
            case "doc" -> appendChildren(parent, node);
            case "paragraph" -> appendChildren(aligned(parent.appendElement("p"), attrs), node);
            case "heading" -> {
                int level = (int) number(attrs == null ? null : attrs.get("level"), 1);
                appendChildren(aligned(parent.appendElement("h" + (level >= 1 && level <= 3 ? level : 1)), attrs), node);
            }
            case "bulletList" -> appendChildren(parent.appendElement("ul"), node);
            case "orderedList" -> appendChildren(parent.appendElement("ol"), node);
            case "listItem" -> appendChildren(parent.appendElement("li"), node);
            case "blockquote" -> appendChildren(parent.appendElement("blockquote"), node);
            case "codeBlock" -> appendChildren(parent.appendElement("pre").appendElement("code"), node);
            case "hardBreak" -> parent.appendElement("br");
            case "horizontalRule" -> parent.appendElement("hr");
            case "image" -> appendImage(parent, attrs);
            case "text" -> appendText(parent, node);
            default -> throw new IllegalArgumentException("Unsupported node");
        }
    }

    /**
     * 자식 노드를 순서대로 추가
     */
    private static void appendChildren(Element parent, JsonNode node) {
        JsonNode content = node.get("content");
        if (content == null) return;
        for (JsonNode child : content) appendNode(parent, child);
    }

    /**
     * 문단 정렬 속성 반영
     *
     * @return 같은 요소
     */
    private static Element aligned(Element element, JsonNode attrs) {
        String align = attrs == null ? null : text(attrs.get("textAlign"));
        if (align != null && !align.isEmpty()) element.attr("style", "text-align: " + align);
        return element;
    }

    /**
     * 사진을 {@code figure > img + figcaption}으로 추가
     *
     * 너비(%)와 좌·우·가운데 정렬을 {@code figure}의 스타일로 표현
     */
    private static void appendImage(Element parent, JsonNode attrs) {
        String caption = attrs == null ? "" : jsString(attrs.get("caption"), "");
        String align = attrs == null ? "center" : jsString(attrs.get("align"), "center");
        long width = Math.round(number(attrs == null ? null : attrs.get("width"), 100));
        Element figure = parent.appendElement("figure").attr("style", "width:" + width + "%;margin-left:"
                + ("left".equals(align) ? "0" : "auto") + ";margin-right:" + ("right".equals(align) ? "0" : "auto"));
        String alt = attrs == null ? null : text(attrs.get("alt"));
        figure.appendElement("img")
                .attr("src", attrs == null ? "" : jsString(attrs.get("src"), ""))
                .attr("alt", alt != null ? alt : caption)
                .attr("style", "width:100%;height:auto");
        figure.appendElement("figcaption").text(caption);
    }

    /**
     * 텍스트와 마크 추가, 앞선 마크가 바깥쪽
     *
     * @throws IllegalArgumentException 텍스트가 없거나 비어 있을 때
     */
    private static void appendText(Element parent, JsonNode node) {
        String value = text(node.get("text"));
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("Empty text nodes are not allowed");
        Element target = parent;
        JsonNode marks = node.get("marks");
        if (marks != null) {
            for (JsonNode mark : marks) {
                target = switch (text(mark.get("type"))) {
                    case "bold" -> target.appendElement("strong");
                    case "italic" -> target.appendElement("em");
                    case "underline" -> target.appendElement("u");
                    case "strike" -> target.appendElement("s");
                    case "code" -> target.appendElement("code");
                    case "link" -> {
                        JsonNode markAttrs = mark.get("attrs");
                        yield target.appendElement("a").attr("href",
                                markAttrs == null ? "" : jsString(markAttrs.get("href"), ""));
                    }
                    default -> target;
                };
            }
        }
        target.appendChild(new TextNode(value));
    }

    /**
     * 문자열 노드 값
     *
     * @return 문자열이 아니면 {@code null}
     */
    private static String text(JsonNode node) {
        return node != null && node.isString() ? node.stringValue() : null;
    }

    /**
     * JavaScript {@code String(value ?? fallback)}과 같은 문자열 변환
     *
     * @return 없거나 {@code null}이면 기본값, 그 외에는 문자열 표현
     */
    private static String jsString(JsonNode node, String fallback) {
        if (node == null || node.isNull() || node.isMissingNode()) return fallback;
        return node.isString() ? node.stringValue() : node.toString();
    }

    /**
     * JavaScript {@code Number(value ?? fallback)}과 같은 숫자 변환
     *
     * @return 변환할 수 없으면 {@code NaN}
     */
    private static double number(JsonNode node, double fallback) {
        if (node == null || node.isNull() || node.isMissingNode()) return fallback;
        if (node.isNumber()) return node.doubleValue();
        if (node.isString()) {
            try {
                return Double.parseDouble(node.stringValue().trim());
            } catch (NumberFormatException invalid) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    /**
     * 사진 노드 목록을 문서 끝에 추가할 배열
     *
     * @return 문서의 {@code content} 배열, 없으면 새로 만들어 연결
     */
    static ArrayNode contentOf(ObjectNode document) {
        if (document.get("content") instanceof ArrayNode content) return content;
        ArrayNode content = NODES.arrayNode();
        document.set("content", content);
        return content;
    }
}

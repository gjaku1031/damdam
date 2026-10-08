package io.github.gjaku1031.damdam.feed.document;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import io.github.gjaku1031.damdam.storage.service.ImageKeys;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 문서 JSON이 없는 기존 HTML 게시글을 TipTap 문서로 변환
 *
 * 정제한 HTML을 에디터가 지원하는 노드·마크로만 해석
 * 본문에 없던 첨부 사진은 문서 끝에 사진 노드로 추가
 */
public final class LegacyHtmlConverter {
    /**
     * 노드 생성기
     */
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    /**
     * 블록으로 해석하는 태그
     */
    private static final Set<String> BLOCK_TAGS = Set.of("p", "h1", "h2", "h3", "ul", "ol", "blockquote", "pre", "hr",
            "figure", "img");

    /**
     * 인라인 태그와 마크 종류
     */
    private static final Map<String, String> MARKS = Map.of("strong", "bold", "b", "bold", "em", "italic", "i",
            "italic", "u", "underline", "s", "strike", "del", "strike", "code", "code", "a", "link");

    /**
     * 스타일의 문단 정렬 값
     */
    private static final Pattern TEXT_ALIGN = Pattern.compile("text-align:\\s*(left|center|right)");

    /**
     * 스타일의 너비(%) 값
     */
    private static final Pattern WIDTH = Pattern.compile("(?:^|;)\\s*width:\\s*(\\d+)%");

    /**
     * 유틸리티 클래스 생성 방지
     */
    private LegacyHtmlConverter() {}

    /**
     * HTML 게시글 변환
     *
     * @param html 기존 본문 HTML, 변환 전에 정제
     * @param attachedKeys 게시글 첨부 사진 키(표시 순서)
     * @return TipTap 문서, 본문에 없던 첨부 사진은 끝에 추가
     */
    public static ObjectNode convert(String html, List<String> attachedKeys) {
        ObjectNode document = NODES.objectNode().put("type", "doc");
        ArrayNode content = PostDocuments.contentOf(document);
        appendBlocks(content, Jsoup.parseBodyFragment(HtmlSanitizer.clean(html)).body());

        // 본문에서 참조하지 않은 첨부 사진 보존
        List<String> keys = new ArrayList<>(PostDocuments.imageKeys(document));
        for (String key : attachedKeys) {
            if (keys.contains(key)) continue;
            content.add(PostDocuments.imageNode(key));
            keys.add(key);
        }
        return document;
    }

    /**
     * 블록 노드 추가
     *
     * 블록 사이에 놓인 텍스트·인라인 요소는 문단 하나로 묶음
     */
    private static void appendBlocks(ArrayNode target, Element parent) {
        ObjectNode loose = null;
        for (Node child : parent.childNodes()) {
            if (child instanceof Element element && BLOCK_TAGS.contains(element.normalName())) {
                loose = null;
                appendBlock(target, element);
            } else {
                if (child instanceof TextNode text && text.isBlank()) continue;
                if (loose == null) {
                    loose = NODES.objectNode().put("type", "paragraph");
                    target.add(loose);
                }
                appendInline(PostDocuments.contentOf(loose), child, NODES.arrayNode());
            }
        }
    }

    /**
     * 블록 요소 하나를 노드로 추가
     */
    private static void appendBlock(ArrayNode target, Element element) {
        String tag = element.normalName();
        switch (tag) {
            case "p", "h1", "h2", "h3" -> {
                ObjectNode block = NODES.objectNode().put("type", "p".equals(tag) ? "paragraph" : "heading");
                ObjectNode attrs = NODES.objectNode();
                if (!"p".equals(tag)) attrs.put("level", tag.charAt(1) - '0');
                Matcher align = TEXT_ALIGN.matcher(element.attr("style"));
                if (align.find()) attrs.put("textAlign", align.group(1));
                if (!attrs.isEmpty()) block.set("attrs", attrs);
                appendInlineChildren(PostDocuments.contentOf(block), element, NODES.arrayNode());
                target.add(block);
            }
            case "ul", "ol" -> {
                ObjectNode list = NODES.objectNode().put("type", "ul".equals(tag) ? "bulletList" : "orderedList");
                for (Element item : element.children()) {
                    if (!"li".equals(item.normalName())) continue;
                    ObjectNode listItem = NODES.objectNode().put("type", "listItem");
                    appendBlocks(PostDocuments.contentOf(listItem), item);
                    PostDocuments.contentOf(list).add(listItem);
                }
                target.add(list);
            }
            case "blockquote" -> {
                ObjectNode quote = NODES.objectNode().put("type", "blockquote");
                appendBlocks(PostDocuments.contentOf(quote), element);
                target.add(quote);
            }
            case "pre" -> {
                ObjectNode code = NODES.objectNode().put("type", "codeBlock");
                if (!element.wholeText().isEmpty()) {
                    PostDocuments.contentOf(code).add(NODES.objectNode().put("type", "text").put("text", element.wholeText()));
                }
                target.add(code);
            }
            case "hr" -> target.add(NODES.objectNode().put("type", "horizontalRule"));
            case "figure", "img" -> appendImage(target, element);
            default -> appendBlocks(target, element);
        }
    }

    /**
     * {@code figure} 또는 {@code img}를 사진 노드로 추가
     *
     * 주소가 업로드·기존 정적 사진이 아니면 건너뜀
     */
    private static void appendImage(ArrayNode target, Element element) {
        Element img = "img".equals(element.normalName()) ? element : element.selectFirst("img");
        if (img == null) return;
        String src = img.attr("src");
        String key = src.startsWith(ImageKeys.FILE_URL_PREFIX) ? src.substring(ImageKeys.FILE_URL_PREFIX.length()) : src;
        if (!ImageKeys.ATTACHABLE.matcher(key).matches() || key.contains("..")) return;

        // figure 스타일의 너비·여백으로 크기·정렬 복원
        String style = element.attr("style").replace(" ", "");
        Matcher width = WIDTH.matcher(style);
        String align = style.matches(".*margin-left:0(px)?(;.*|$)") ? "left"
                : style.matches(".*margin-right:0(px)?(;.*|$)") ? "right" : "center";
        Element caption = element.selectFirst("figcaption");
        ObjectNode image = PostDocuments.imageNode(key);
        ObjectNode attrs = (ObjectNode) image.get("attrs");
        attrs.put("width", width.find() ? Integer.parseInt(width.group(1)) : 100)
                .put("align", align)
                .put("caption", caption == null ? "" : caption.text());
        if (img.hasAttr("alt")) attrs.put("alt", img.attr("alt"));
        target.add(image);
    }

    /**
     * 요소의 자식을 인라인 노드로 추가
     */
    private static void appendInlineChildren(ArrayNode target, Element element, ArrayNode marks) {
        for (Node child : element.childNodes()) appendInline(target, child, marks);
    }

    /**
     * 텍스트·줄바꿈·마크 요소를 인라인 노드로 추가
     *
     * @param marks 바깥 요소에서 이어받은 마크, 앞선 마크가 바깥쪽
     */
    private static void appendInline(ArrayNode target, Node node, ArrayNode marks) {
        if (node instanceof TextNode text) {
            if (text.getWholeText().isEmpty()) return;
            ObjectNode textNode = NODES.objectNode().put("type", "text").put("text", text.getWholeText());
            if (!marks.isEmpty()) textNode.set("marks", marks.deepCopy());
            target.add(textNode);
            return;
        }
        if (!(node instanceof Element element)) return;
        String tag = element.normalName().toLowerCase(Locale.ROOT);
        if ("br".equals(tag)) {
            target.add(NODES.objectNode().put("type", "hardBreak"));
            return;
        }

        // 마크 요소는 마크를 덧붙여 자식을 이어서 해석
        String mark = MARKS.get(tag);
        ArrayNode nested = marks;
        if (mark != null) {
            nested = marks.deepCopy();
            ObjectNode markNode = NODES.objectNode().put("type", mark);
            if ("link".equals(mark)) markNode.set("attrs", NODES.objectNode().put("href", element.attr("href")));
            nested.add(markNode);
        }
        appendInlineChildren(target, element, nested);
    }
}

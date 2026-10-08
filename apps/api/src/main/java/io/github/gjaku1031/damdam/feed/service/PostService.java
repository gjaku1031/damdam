package io.github.gjaku1031.damdam.feed.service;

import static io.github.gjaku1031.damdam.jooq.Tables.ADMIN_UPDATES;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.gjaku1031.damdam.common.error.ApiException;
import io.github.gjaku1031.damdam.common.web.ItemPage;
import io.github.gjaku1031.damdam.common.web.Paging;
import io.github.gjaku1031.damdam.feed.document.LegacyHtmlConverter;
import io.github.gjaku1031.damdam.feed.document.PostDocuments;
import io.github.gjaku1031.damdam.feed.domain.FeedPostImageEntity;
import io.github.gjaku1031.damdam.feed.domain.PostCategoryEntity;
import io.github.gjaku1031.damdam.feed.domain.PostState;
import io.github.gjaku1031.damdam.feed.dto.AdjacentPostsResponse;
import io.github.gjaku1031.damdam.feed.dto.CategoryResponse;
import io.github.gjaku1031.damdam.feed.dto.DraftRequest;
import io.github.gjaku1031.damdam.feed.dto.DraftView;
import io.github.gjaku1031.damdam.feed.dto.PostLink;
import io.github.gjaku1031.damdam.feed.dto.PostView;
import io.github.gjaku1031.damdam.feed.repository.FeedPostImageRepository;
import io.github.gjaku1031.damdam.feed.repository.PostCategoryRepository;
import io.github.gjaku1031.damdam.jooq.tables.records.AdminUpdatesRecord;
import io.github.gjaku1031.damdam.storage.service.StorageService;
import io.github.gjaku1031.damdam.system.domain.AdminAuditAction;
import io.github.gjaku1031.damdam.system.service.SystemService;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 블로그 게시글 조회·초안·발행
 *
 * 게시글 행({@code admin_updates})은 jsonb 초안과 낙관적 리비전 갱신이 많아 jOOQ로 처리
 * 발행본(제목·본문·태그·분류·대표 사진)과 작성 중인 초안({@code draft})을 분리해, 초안 저장이 공개 화면에 영향을 주지 않음
 */
@Service
@RequiredArgsConstructor
public class PostService {
    /**
     * 운영 기록 대상 종류
     */
    private static final String AUDIT_TARGET = "POST";

    /**
     * 요약용 태그 제거
     */
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");

    /**
     * 요약용 공백 정리(유니코드 공백 포함)
     */
    private static final Pattern SPACES = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    /**
     * 게시글 jOOQ 접근
     */
    private final DSLContext dsl;

    /**
     * jsonb 직렬화
     */
    private final JsonMapper json;

    /**
     * 분류 저장소
     */
    private final PostCategoryRepository categories;

    /**
     * 발행 사진 목록 저장소
     */
    private final FeedPostImageRepository images;

    /**
     * 사진 업로드 검증
     */
    private final StorageService storage;

    /**
     * 운영 기록
     */
    private final SystemService audit;

    /**
     * 게시글 행 조회
     *
     * @param publicOnly {@code true}면 공개 발행 글만
     * @return 삭제되지 않은 게시글 행
     * @throws ApiException 없거나 조건에 맞지 않을 때(404)
     */
    @Transactional(readOnly = true)
    public AdminUpdatesRecord find(UUID id, boolean publicOnly) {
        Condition condition = ADMIN_UPDATES.ID.eq(id).and(ADMIN_UPDATES.DELETEDAT.isNull());
        if (publicOnly) condition = condition.and(publicCondition());
        return dsl.selectFrom(ADMIN_UPDATES).where(condition).fetchOptional()
                .orElseThrow(() -> ApiException.notFound("게시글을 찾을 수 없습니다."));
    }

    /**
     * 게시글 목록
     *
     * @param publicOnly {@code true}면 공개 발행 글을 발행본 제목으로 검색, {@code false}면 초안 제목도 검색
     * @param search 제목 부분 일치(대소문자 무시)
     * @param categoryId 분류 ID 문자열, 비어 있으면 전체, 형식이 잘못되면 결과 없음
     * @param state 운영 목록의 상태 필터(DRAFT·PUBLISHED·PRIVATE), 그 외 값은 무시
     * @return 최근 발행 순(미발행은 뒤), 같은 발행 시각이면 최근 생성 순
     */
    @Transactional(readOnly = true)
    public ItemPage<PostView> list(boolean publicOnly, Paging paging, String search, String categoryId, String state) {
        Condition condition = ADMIN_UPDATES.DELETEDAT.isNull();
        if (publicOnly) condition = condition.and(publicCondition());

        // 제목 검색: 운영 목록은 작성 중인 초안 제목도 포함
        if (search != null && !search.isEmpty()) {
            String pattern = "%" + search + "%";
            Condition title = ADMIN_UPDATES.TITLE.likeIgnoreCase(pattern);
            condition = condition.and(publicOnly ? title
                    : title.or(DSL.condition("{0}->>'title' ILIKE {1}", ADMIN_UPDATES.DRAFT, pattern)));
        }
        if (!publicOnly && Set.of("DRAFT", "PUBLISHED", "PRIVATE").contains(state)) {
            condition = condition.and(ADMIN_UPDATES.STATE.eq(state));
        }
        if (categoryId != null && !categoryId.isEmpty()) {
            UUID category = parseUuid(categoryId);
            condition = condition.and(category == null ? DSL.falseCondition()
                    : ADMIN_UPDATES.CATEGORYID.eq(category));
        }

        List<AdminUpdatesRecord> posts = dsl.selectFrom(ADMIN_UPDATES).where(condition)
                .orderBy(ADMIN_UPDATES.PUBLISHEDAT.desc().nullsLast(), ADMIN_UPDATES.CREATEDAT.desc())
                .limit(paging.size()).offset(paging.offset())
                .fetch();
        Map<UUID, CategoryResponse> categoryById = categoriesOf(posts);
        List<PostView> items = posts.stream().map(post -> view(post, !publicOnly, true, categoryById)).toList();
        return ItemPage.of(items, dsl.fetchCount(ADMIN_UPDATES, condition), paging);
    }

    /**
     * 공개 게시글 상세
     *
     * @return 발행 본문 HTML을 포함한 공개 응답
     * @throws ApiException 공개 발행 글이 아닐 때(404)
     */
    @Transactional(readOnly = true)
    public PostView publicDetail(UUID id) {
        AdminUpdatesRecord post = find(id, true);
        return view(post, false, false, categoriesOf(List.of(post)));
    }

    /**
     * 공개 게시글의 이전·다음 공개 글 조회
     *
     * 발행 시각·ID 쌍으로 비교해 같은 발행 시각에서도 순서가 하나로 정해짐
     *
     * @return 더 오래된 글(previous)과 더 최근 글(next), 없으면 각각 {@code null}
     * @throws ApiException 기준 글이 공개 발행 글이 아닐 때(404)
     */
    @Transactional(readOnly = true)
    public AdjacentPostsResponse adjacent(UUID id) {
        AdminUpdatesRecord post = find(id, true);
        // 공개 발행 글 중 기준 글 앞뒤의 한 건씩
        Condition visible = ADMIN_UPDATES.DELETEDAT.isNull().and(publicCondition());
        var key = DSL.row(ADMIN_UPDATES.PUBLISHEDAT, ADMIN_UPDATES.ID);
        var current = DSL.row(post.getPublishedat(), post.getId());
        PostLink previous = dsl.select(ADMIN_UPDATES.ID, ADMIN_UPDATES.TITLE).from(ADMIN_UPDATES)
                .where(visible, key.lt(current))
                .orderBy(ADMIN_UPDATES.PUBLISHEDAT.desc(), ADMIN_UPDATES.ID.desc()).limit(1)
                .fetchOptional(row -> new PostLink(row.value1(), row.value2())).orElse(null);
        PostLink next = dsl.select(ADMIN_UPDATES.ID, ADMIN_UPDATES.TITLE).from(ADMIN_UPDATES)
                .where(visible, key.gt(current))
                .orderBy(ADMIN_UPDATES.PUBLISHEDAT.asc(), ADMIN_UPDATES.ID.asc()).limit(1)
                .fetchOptional(row -> new PostLink(row.value1(), row.value2())).orElse(null);
        return new AdjacentPostsResponse(previous, next);
    }

    /**
     * 빈 초안 생성
     *
     * @param authorUserId 작성 운영자 ID
     * @return 생성한 초안
     */
    @Transactional
    public DraftView create(UUID authorUserId) {
        // 본문은 빈 문단 하나, 공개 전까지 숨김
        ObjectNode document = json.createObjectNode().put("type", "doc");
        document.putArray("content").addObject().put("type", "paragraph");
        ObjectNode draft = draftNode("", document, json.createArrayNode(), null, null);
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        dsl.insertInto(ADMIN_UPDATES)
                .set(ADMIN_UPDATES.ID, id)
                .set(ADMIN_UPDATES.CREATEDAT, now)
                .set(ADMIN_UPDATES.UPDATEDAT, now)
                .set(ADMIN_UPDATES.TITLE, "")
                .set(ADMIN_UPDATES.BODYHTML, "")
                .set(ADMIN_UPDATES.VISIBLE, false)
                .set(ADMIN_UPDATES.STATE, PostState.DRAFT.name())
                .set(ADMIN_UPDATES.AUTHOR_USER_ID, authorUserId)
                .set(ADMIN_UPDATES.DRAFT, JSONB.valueOf(draft.toString()))
                .execute();
        audit.log(authorUserId, AdminAuditAction.CREATE, AUDIT_TARGET, id.toString(), "임시글 생성");
        return draft(id);
    }

    /**
     * 편집 화면용 초안 조회
     *
     * @return 저장된 초안, 초안이 없으면 발행본으로 구성한 초안
     * @throws ApiException 게시글이 없을 때(404)
     */
    @Transactional(readOnly = true)
    public DraftView draft(UUID id) {
        AdminUpdatesRecord post = find(id, false);
        JsonNode draft = post.getDraft() != null ? read(post.getDraft()) : publishedAsDraft(post);
        return new DraftView(id, PostState.valueOf(post.getState()), post.getRevision(), post.getPublishedat(), draft);
    }

    /**
     * 초안 저장
     *
     * 1. 문서 검증·사진 주소 정규화, 본문 사진이 검증된 업로드인지 확인
     * 2. 대표 사진(본문 사진 중 하나, 없으면 첫 사진)과 분류 확인
     * 3. 리비전이 같을 때만 초안을 바꾸고 리비전 증가(다른 화면의 저장을 덮어쓰지 않음)
     *
     * @return 저장 후 초안
     * @throws ApiException 문서·사진·분류 오류(400), 게시글 없음(404), 리비전 불일치(409)
     */
    @Transactional
    public DraftView saveDraft(UUID id, DraftRequest request) {
        JsonNode document = request.documentJson();
        PostDocuments.render(document);
        List<String> keys = PostDocuments.imageKeys(document);
        keys.forEach(storage::assertUsable);
        String cover = request.coverObjectKey();
        if (cover != null && !cover.isEmpty() && !keys.contains(cover)) {
            throw ApiException.badRequest("본문 사진에서 대표 이미지를 선택해주세요.");
        }
        if (request.categoryId() != null && !categories.existsById(request.categoryId())) {
            throw ApiException.badRequest("분류를 찾을 수 없습니다.");
        }

        // 태그는 앞뒤 공백 제거 후 빈 값·중복 제거
        ArrayNode tags = json.createArrayNode();
        new LinkedHashSet<>(request.tags().stream().map(String::trim).filter(tag -> !tag.isEmpty()).toList())
                .forEach(tags::add);
        String coverKey = cover != null && !cover.isEmpty() ? cover : keys.isEmpty() ? null : keys.getFirst();
        ObjectNode draft = draftNode(request.title(), document, tags, request.categoryId(), coverKey);

        int updated = dsl.update(ADMIN_UPDATES)
                .set(ADMIN_UPDATES.DRAFT, JSONB.valueOf(draft.toString()))
                .set(ADMIN_UPDATES.REVISION, ADMIN_UPDATES.REVISION.plus(1))
                .set(ADMIN_UPDATES.UPDATEDAT, Instant.now())
                .where(ADMIN_UPDATES.ID.eq(id), ADMIN_UPDATES.REVISION.eq(request.revision()),
                        ADMIN_UPDATES.DELETEDAT.isNull())
                .execute();
        if (updated == 0) {
            find(id, false);
            throw ApiException.conflict("다른 화면에서 글이 변경되었습니다. 작성 내용을 보관하고 다시 불러와주세요.");
        }
        return draft(id);
    }

    /**
     * 초안 발행
     *
     * 1. 게시글 행 잠금 후 리비전 확인
     * 2. 초안(없으면 현재 발행본)의 제목·문서 검증, HTML 렌더링, 사진 업로드 확인
     * 3. 공개 사진 목록 교체, 발행본 필드 갱신(최초 발행 시각 유지), 리비전 증가
     *
     * 2·3단계 실패 시 사진 목록과 발행본 변경이 함께 롤백됨
     *
     * @param actorUserId 발행한 운영자 ID
     * @return 발행 후 초안
     * @throws ApiException 게시글 없음(404), 리비전 불일치(409), 제목·문서 오류(400)
     */
    @Transactional
    public DraftView publish(UUID id, int revision, UUID actorUserId) {
        AdminUpdatesRecord post = dsl.selectFrom(ADMIN_UPDATES)
                .where(ADMIN_UPDATES.ID.eq(id), ADMIN_UPDATES.DELETEDAT.isNull())
                .forUpdate().fetchOptional().orElseThrow(ApiException::notFound);
        if (post.getRevision() != revision) throw ApiException.conflict("다른 화면에서 글이 변경되었습니다.");

        // 발행할 내용 검증과 렌더링
        JsonNode draft = post.getDraft() != null ? read(post.getDraft()) : publishedAsDraft(post);
        String title = text(draft.get("title"));
        if (title == null || title.isBlank()) throw ApiException.badRequest("제목을 입력해주세요.");
        JsonNode document = draft.get("documentJson");
        String html = PostDocuments.render(document);
        List<String> keys = PostDocuments.imageKeys(document);
        keys.forEach(storage::assertUsable);

        // 공개 사진 접근 권한의 기준이 되는 사진 목록 교체
        images.deleteAllOfPost(id);
        List<FeedPostImageEntity> next = new ArrayList<>();
        for (int sortOrder = 0; sortOrder < keys.size(); sortOrder++) {
            next.add(FeedPostImageEntity.of(id, keys.get(sortOrder), sortOrder));
        }
        images.saveAllAndFlush(next);

        // 발행본 갱신, 최초 발행 시각은 유지
        Instant now = Instant.now();
        String categoryId = text(draft.get("categoryId"));
        dsl.update(ADMIN_UPDATES)
                .set(ADMIN_UPDATES.TITLE, title.trim())
                .set(ADMIN_UPDATES.BODYHTML, html)
                .set(ADMIN_UPDATES.DOCUMENTJSON, JSONB.valueOf(document.toString()))
                .set(ADMIN_UPDATES.TAGS, JSONB.valueOf(draft.has("tags") ? draft.get("tags").toString() : "[]"))
                .set(ADMIN_UPDATES.CATEGORYID, categoryId == null ? null : parseUuid(categoryId))
                .set(ADMIN_UPDATES.COVEROBJECTKEY, text(draft.get("coverObjectKey")))
                .set(ADMIN_UPDATES.STATE, PostState.PUBLISHED.name())
                .set(ADMIN_UPDATES.VISIBLE, true)
                .set(ADMIN_UPDATES.PUBLISHEDAT, post.getPublishedat() != null ? post.getPublishedat() : now)
                .set(ADMIN_UPDATES.PUBLISHEDUPDATEDAT, now)
                .set(ADMIN_UPDATES.REVISION, post.getRevision() + 1)
                .set(ADMIN_UPDATES.UPDATEDAT, now)
                .where(ADMIN_UPDATES.ID.eq(id))
                .execute();
        audit.log(actorUserId, AdminAuditAction.UPDATE, AUDIT_TARGET, id.toString(), "게시글 발행");
        return draft(id);
    }

    /**
     * 게시글 비공개
     *
     * 리비전이 같을 때만 변경, 공개 사진 접근도 함께 막힘
     *
     * @return 변경 후 초안
     * @throws ApiException 게시글 없음(404), 리비전 불일치(409)
     */
    @Transactional
    public DraftView hide(UUID id, int revision, UUID actorUserId) {
        int updated = dsl.update(ADMIN_UPDATES)
                .set(ADMIN_UPDATES.VISIBLE, false)
                .set(ADMIN_UPDATES.STATE, PostState.PRIVATE.name())
                .set(ADMIN_UPDATES.REVISION, ADMIN_UPDATES.REVISION.plus(1))
                .set(ADMIN_UPDATES.UPDATEDAT, Instant.now())
                .where(ADMIN_UPDATES.ID.eq(id), ADMIN_UPDATES.REVISION.eq(revision), ADMIN_UPDATES.DELETEDAT.isNull())
                .execute();
        if (updated == 0) {
            find(id, false);
            throw ApiException.conflict("다른 화면에서 글이 변경되었습니다.");
        }
        audit.log(actorUserId, AdminAuditAction.UPDATE, AUDIT_TARGET, id.toString(), "게시글 비공개");
        return draft(id);
    }

    /**
     * 게시글 소프트 삭제
     *
     * @throws ApiException 게시글이 없을 때(404)
     */
    @Transactional
    public void remove(UUID id, UUID actorUserId) {
        find(id, false);
        Instant now = Instant.now();
        dsl.update(ADMIN_UPDATES)
                .set(ADMIN_UPDATES.DELETEDAT, now)
                .set(ADMIN_UPDATES.UPDATEDAT, now)
                .where(ADMIN_UPDATES.ID.eq(id))
                .execute();
        audit.log(actorUserId, AdminAuditAction.DELETE, AUDIT_TARGET, id.toString(), null);
    }

    /**
     * 공개 발행 글 조건
     *
     * @return 공개이면서 발행 상태
     */
    private static Condition publicCondition() {
        return ADMIN_UPDATES.VISIBLE.isTrue().and(ADMIN_UPDATES.STATE.eq(PostState.PUBLISHED.name()));
    }

    /**
     * 게시글 응답 조립
     *
     * @param admin {@code true}면 초안 제목·대표 사진 우선, 상태·리비전 포함
     * @param summary {@code true}면 본문 HTML 생략
     * @param categoryById 미리 조회한 분류
     * @return 응답
     */
    private PostView view(AdminUpdatesRecord post, boolean admin, boolean summary,
            Map<UUID, CategoryResponse> categoryById) {
        JsonNode draft = admin && post.getDraft() != null ? read(post.getDraft()) : null;
        String draftTitle = draft == null ? null : text(draft.get("title"));
        String draftCover = draft == null ? null : text(draft.get("coverObjectKey"));
        String excerpt = SPACES.matcher(TAGS.matcher(post.getBodyhtml()).replaceAll(" ")).replaceAll(" ").trim();

        // 문서 JSON이 없는 기존 글은 기존 HTML을 변환해 렌더링
        String bodyHtml = summary ? null
                : post.getDocumentjson() != null ? post.getBodyhtml() : PostDocuments.render(publishedDocument(post));
        return new PostView(post.getId(),
                draftTitle != null ? draftTitle : post.getTitle(),
                excerpt.length() > 140 ? excerpt.substring(0, 140) : excerpt,
                bodyHtml,
                draftCover != null ? draftCover : post.getCoverobjectkey(),
                post.getCategoryid(),
                post.getCategoryid() == null ? null : categoryById.get(post.getCategoryid()),
                tags(post),
                post.getCommentcount(),
                post.getCreatedat(),
                admin || post.getPublishedupdatedat() == null ? post.getUpdatedat() : post.getPublishedupdatedat(),
                post.getPublishedat(),
                admin ? PostState.valueOf(post.getState()) : null,
                admin ? post.getRevision() : null);
    }

    /**
     * 게시글들의 분류 일괄 조회
     *
     * @return 분류 ID별 응답
     */
    private Map<UUID, CategoryResponse> categoriesOf(List<AdminUpdatesRecord> posts) {
        List<UUID> ids = posts.stream().map(AdminUpdatesRecord::getCategoryid).filter(Objects::nonNull).distinct()
                .toList();
        if (ids.isEmpty()) return Map.of();
        return categories.findAllById(ids).stream()
                .collect(Collectors.toMap(PostCategoryEntity::getId, CategoryResponse::from, (a, b) -> a));
    }

    /**
     * 현재 발행본으로 초안 구성
     *
     * @return {@code title, documentJson, tags, categoryId, coverObjectKey}
     */
    private ObjectNode publishedAsDraft(AdminUpdatesRecord post) {
        ArrayNode tags = json.createArrayNode();
        tags(post).forEach(tags::add);
        return draftNode(post.getTitle(), publishedDocument(post), tags, post.getCategoryid(), post.getCoverobjectkey());
    }

    /**
     * 발행 문서
     *
     * @return 저장된 문서 JSON, 없으면 기존 HTML과 사진 목록을 변환한 문서
     */
    private JsonNode publishedDocument(AdminUpdatesRecord post) {
        if (post.getDocumentjson() != null) return read(post.getDocumentjson());
        List<String> attached = images.findByPostIdOrderBySortOrderAsc(post.getId()).stream()
                .map(FeedPostImageEntity::getObjectKey).toList();
        return LegacyHtmlConverter.convert(post.getBodyhtml(), attached);
    }

    /**
     * 초안 JSON 생성
     *
     * @return 키 순서가 고정된 초안 객체
     */
    private ObjectNode draftNode(String title, JsonNode document, ArrayNode tags, UUID categoryId, String cover) {
        ObjectNode draft = json.createObjectNode().put("title", title);
        draft.set("documentJson", document);
        draft.set("tags", tags);
        draft.put("categoryId", categoryId == null ? null : categoryId.toString());
        draft.put("coverObjectKey", cover);
        return draft;
    }

    /**
     * 발행본 태그 목록
     *
     * @return 태그, 저장된 값이 배열이 아니면 빈 목록
     */
    private List<String> tags(AdminUpdatesRecord post) {
        JsonNode tags = post.getTags() == null ? null : read(post.getTags());
        if (tags == null || !tags.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        tags.forEach(tag -> values.add(tag.isString() ? tag.stringValue() : tag.toString()));
        return values;
    }

    /**
     * jsonb 값 파싱
     *
     * @return JSON 트리
     */
    private JsonNode read(JSONB value) {
        return json.readTree(value.data());
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
     * UUID 문자열 해석
     *
     * @return 형식이 잘못되면 {@code null}
     */
    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}

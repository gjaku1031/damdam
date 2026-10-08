package io.github.gjaku1031.damdam.cms.service;

import static io.github.gjaku1031.damdam.jooq.Tables.CMS_CONTENTS;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.gjaku1031.damdam.cms.domain.CmsContentEntity;
import io.github.gjaku1031.damdam.cms.domain.CmsContentType;
import io.github.gjaku1031.damdam.cms.dto.CmsContentResponse;
import io.github.gjaku1031.damdam.cms.repository.CmsContentRepository;
import io.github.gjaku1031.damdam.common.error.ApiException;
import io.github.gjaku1031.damdam.common.web.ItemPage;
import io.github.gjaku1031.damdam.common.web.Paging;
import io.github.gjaku1031.damdam.feed.document.HtmlSanitizer;
import lombok.RequiredArgsConstructor;

/**
 * 공지사항·FAQ 조회와 관리
 *
 * 본문은 저장할 때마다 {@link HtmlSanitizer}로 정제
 */
@Service
@RequiredArgsConstructor
public class CmsService {
    /**
     * 공지·FAQ 저장소
     */
    private final CmsContentRepository contents;

    /**
     * 목록·건수 조회용 jOOQ
     */
    private final DSLContext dsl;

    /**
     * 목록 조회
     *
     * @param visibility {@code public}·{@code hidden}이면 공개·숨김만, 그 외는 전체(삭제 제외)
     * @return 최근 작성 순
     */
    @Transactional(readOnly = true)
    public ItemPage<CmsContentResponse> list(CmsContentType type, Paging paging, String visibility) {
        Condition condition = CMS_CONTENTS.TYPE.eq(type.name()).and(CMS_CONTENTS.DELETEDAT.isNull());
        if ("public".equals(visibility)) condition = condition.and(CMS_CONTENTS.VISIBLE.isTrue());
        if ("hidden".equals(visibility)) condition = condition.and(CMS_CONTENTS.VISIBLE.isFalse());

        // 쪽의 ID를 고른 뒤 엔티티로 조회해 순서대로 응답
        List<UUID> ids = dsl.select(CMS_CONTENTS.ID).from(CMS_CONTENTS).where(condition)
                .orderBy(CMS_CONTENTS.CREATEDAT.desc(), CMS_CONTENTS.ID.desc())
                .limit(paging.size()).offset(paging.offset())
                .fetch(CMS_CONTENTS.ID);
        Map<UUID, CmsContentEntity> byId = contents.findAllById(ids).stream()
                .collect(Collectors.toMap(CmsContentEntity::getId, Function.identity()));
        return ItemPage.of(ids.stream().map(byId::get).map(CmsContentResponse::from).toList(),
                dsl.fetchCount(CMS_CONTENTS, condition), paging);
    }

    /**
     * 상세 조회
     *
     * @param publicOnly {@code true}면 공개 글만
     * @return 글
     * @throws ApiException 없거나 삭제·비공개일 때(404)
     */
    @Transactional(readOnly = true)
    public CmsContentResponse get(UUID id, boolean publicOnly) {
        return CmsContentResponse.from(find(id, publicOnly));
    }

    /**
     * 공개 상태로 등록
     *
     * @return 등록한 글
     */
    @Transactional
    public CmsContentResponse create(CmsContentType type, String title, String bodyHtml, UUID authorUserId) {
        CmsContentEntity content = contents.save(
                CmsContentEntity.create(type, title, HtmlSanitizer.clean(bodyHtml), authorUserId));
        return CmsContentResponse.from(content);
    }

    /**
     * 제목·본문·공개 여부 수정
     *
     * @return 수정한 글
     * @throws ApiException 없거나 삭제됐을 때(404)
     */
    @Transactional
    public CmsContentResponse update(UUID id, String title, String bodyHtml, boolean visible) {
        CmsContentEntity content = find(id, false);
        content.updateContent(title, HtmlSanitizer.clean(bodyHtml), visible);
        contents.flush();
        return CmsContentResponse.from(content);
    }

    /**
     * 공개 여부 변경
     *
     * @return 변경한 글
     * @throws ApiException 없거나 삭제됐을 때(404)
     */
    @Transactional
    public CmsContentResponse setVisible(UUID id, boolean visible) {
        CmsContentEntity content = find(id, false);
        if (visible) content.show();
        else content.hide();
        contents.flush();
        return CmsContentResponse.from(content);
    }

    /**
     * 소프트 삭제
     *
     * @return 삭제 시각이 기록된 글
     * @throws ApiException 없거나 이미 삭제됐을 때(404)
     */
    @Transactional
    public CmsContentResponse remove(UUID id) {
        CmsContentEntity content = find(id, false);
        content.softDelete();
        contents.flush();
        return CmsContentResponse.from(content);
    }

    /**
     * 삭제되지 않은 글 조회
     *
     * @throws ApiException 없거나 조건에 맞지 않을 때(404)
     */
    private CmsContentEntity find(UUID id, boolean publicOnly) {
        return contents.findByIdAndDeletedAtIsNull(id)
                .filter(content -> !publicOnly || content.isVisible())
                .orElseThrow(ApiException::notFound);
    }
}

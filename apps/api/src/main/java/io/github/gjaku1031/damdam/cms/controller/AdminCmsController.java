package io.github.gjaku1031.damdam.cms.controller;

import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.gjaku1031.damdam.cms.domain.CmsContentType;
import io.github.gjaku1031.damdam.cms.dto.CmsContentResponse;
import io.github.gjaku1031.damdam.cms.dto.CreateCmsContentRequest;
import io.github.gjaku1031.damdam.cms.dto.UpdateCmsContentRequest;
import io.github.gjaku1031.damdam.cms.service.CmsService;
import io.github.gjaku1031.damdam.common.auth.CurrentUser;
import io.github.gjaku1031.damdam.common.error.ApiException;
import io.github.gjaku1031.damdam.common.web.ItemPage;
import io.github.gjaku1031.damdam.common.web.Paging;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 공지사항·FAQ API
 *
 * 경로의 {@code notices}·{@code faqs}로 종류 구분
 */
@RestController
@RequestMapping("/admin/cms/{kind:notices|faqs}")
@RequiredArgsConstructor
public final class AdminCmsController {
    /**
     * 공지·FAQ 서비스
     */
    private final CmsService cms;

    /**
     * 목록
     *
     * @param visibility {@code all}·{@code public}·{@code hidden}
     * @return 최근 작성 순, 삭제 제외
     */
    @GetMapping
    public ItemPage<CmsContentResponse> list(@PathVariable String kind, @RequestParam(required = false) String page,
            @RequestParam(required = false) String size, @RequestParam(required = false) String visibility) {
        return cms.list(type(kind), Paging.of(page, size, 20), visibility);
    }

    /**
     * 상세(비공개 포함)
     *
     * @return 글
     */
    @GetMapping("/{id}")
    public CmsContentResponse get(@PathVariable String kind, @PathVariable UUID id) {
        return cms.get(id, false);
    }

    /**
     * 등록
     *
     * @return 등록한 글
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CmsContentResponse create(@PathVariable String kind, @Valid @RequestBody CreateCmsContentRequest body,
            CurrentUser user) {
        return cms.create(type(kind), body.title(), body.bodyHtml(), user.id());
    }

    /**
     * 수정
     *
     * @return 수정한 글
     */
    @PatchMapping("/{id}")
    public CmsContentResponse update(@PathVariable String kind, @PathVariable UUID id,
            @Valid @RequestBody UpdateCmsContentRequest body) {
        return cms.update(id, body.title(), body.bodyHtml(), body.visible());
    }

    /**
     * 공개
     *
     * @return 변경한 글
     */
    @PostMapping("/{id}/show")
    @ResponseStatus(HttpStatus.CREATED)
    public CmsContentResponse show(@PathVariable String kind, @PathVariable UUID id) {
        return cms.setVisible(id, true);
    }

    /**
     * 숨김
     *
     * @return 변경한 글
     */
    @PostMapping("/{id}/hide")
    @ResponseStatus(HttpStatus.CREATED)
    public CmsContentResponse hide(@PathVariable String kind, @PathVariable UUID id) {
        return cms.setVisible(id, false);
    }

    /**
     * 삭제
     *
     * @return 삭제 시각이 기록된 글
     */
    @DeleteMapping("/{id}")
    public CmsContentResponse remove(@PathVariable String kind, @PathVariable UUID id) {
        return cms.remove(id);
    }

    /**
     * 경로 이름을 글 종류로 변환
     *
     * @return {@code notices}면 공지, {@code faqs}면 FAQ
     * @throws ApiException 그 외 경로일 때(404)
     */
    private static CmsContentType type(String kind) {
        return switch (kind.toLowerCase(Locale.ROOT)) {
            case "notices" -> CmsContentType.NOTICE;
            case "faqs" -> CmsContentType.FAQ;
            default -> throw ApiException.notFound();
        };
    }
}

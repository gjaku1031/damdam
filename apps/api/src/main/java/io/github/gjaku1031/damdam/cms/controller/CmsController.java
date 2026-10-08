package io.github.gjaku1031.damdam.cms.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.gjaku1031.damdam.cms.domain.CmsContentType;
import io.github.gjaku1031.damdam.cms.dto.CmsContentResponse;
import io.github.gjaku1031.damdam.cms.service.CmsService;
import io.github.gjaku1031.damdam.common.web.ItemPage;
import io.github.gjaku1031.damdam.common.web.Paging;
import lombok.RequiredArgsConstructor;

/**
 * 공개 공지사항·FAQ API
 */
@RestController
@RequiredArgsConstructor
public final class CmsController {
    /**
     * 공지·FAQ 서비스
     */
    private final CmsService cms;

    /**
     * 공개 공지 목록
     *
     * @return 최근 작성 순
     */
    @GetMapping("/notices")
    public ItemPage<CmsContentResponse> notices(@RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return cms.list(CmsContentType.NOTICE, Paging.of(page, size, 20), "public");
    }

    /**
     * 공개 공지 상세
     *
     * @return 공지
     */
    @GetMapping("/notices/{id}")
    public CmsContentResponse notice(@PathVariable UUID id) {
        return cms.get(id, true);
    }

    /**
     * 공개 FAQ 목록
     *
     * @return 최근 작성 순
     */
    @GetMapping("/faqs")
    public ItemPage<CmsContentResponse> faqs(@RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return cms.list(CmsContentType.FAQ, Paging.of(page, size, 20), "public");
    }

    /**
     * 공개 FAQ 상세
     *
     * @return FAQ
     */
    @GetMapping("/faqs/{id}")
    public CmsContentResponse faq(@PathVariable UUID id) {
        return cms.get(id, true);
    }
}

package io.github.gjaku1031.damdam.feed.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.gjaku1031.damdam.common.web.ItemPage;
import io.github.gjaku1031.damdam.common.web.Paging;
import io.github.gjaku1031.damdam.feed.dto.PostView;
import io.github.gjaku1031.damdam.feed.service.PostService;
import lombok.RequiredArgsConstructor;

/**
 * 이전 피드 주소({@code /api/feed/posts}) 호환 API
 *
 * 외부에 공유된 기존 주소를 위해 공개 게시글 조회만 유지
 */
@RestController
@RequestMapping("/feed")
@RequiredArgsConstructor
public final class LegacyFeedController {
    /**
     * 게시글 서비스
     */
    private final PostService posts;

    /**
     * 공개 게시글 목록
     *
     * @return 발행본 요약
     */
    @GetMapping("/posts")
    public ItemPage<PostView> list(@RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return posts.list(true, Paging.of(page, size, 20), "", "", "");
    }

    /**
     * 공개 게시글 상세
     *
     * @return 발행 본문 HTML 포함
     */
    @GetMapping("/posts/{id}")
    public PostView detail(@PathVariable UUID id) {
        return posts.publicDetail(id);
    }
}

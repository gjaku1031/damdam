package io.github.gjaku1031.damdam.feed.controller;

import java.util.List;
import java.util.Map;
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

import io.github.gjaku1031.damdam.common.auth.CurrentUser;
import io.github.gjaku1031.damdam.common.web.ItemPage;
import io.github.gjaku1031.damdam.common.web.Paging;
import io.github.gjaku1031.damdam.feed.dto.CategoryRequest;
import io.github.gjaku1031.damdam.feed.dto.CategoryResponse;
import io.github.gjaku1031.damdam.feed.dto.CommentResponse;
import io.github.gjaku1031.damdam.feed.dto.DraftRequest;
import io.github.gjaku1031.damdam.feed.dto.DraftView;
import io.github.gjaku1031.damdam.feed.dto.PostView;
import io.github.gjaku1031.damdam.feed.dto.RevisionRequest;
import io.github.gjaku1031.damdam.feed.service.PostCategoryService;
import io.github.gjaku1031.damdam.feed.service.PostCommentService;
import io.github.gjaku1031.damdam.feed.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 운영자 블로그 API: 초안·발행·분류·댓글 관리
 */
@RestController
@RequestMapping("/admin/posts")
@RequiredArgsConstructor
public final class AdminPostController {
    /**
     * 게시글 서비스
     */
    private final PostService posts;

    /**
     * 분류 서비스
     */
    private final PostCategoryService categories;

    /**
     * 댓글 서비스
     */
    private final PostCommentService comments;

    /**
     * 운영 게시글 목록
     *
     * @param state 상태 필터(DRAFT·PUBLISHED·PRIVATE)
     * @return 초안 제목 우선, 상태·리비전 포함
     */
    @GetMapping
    public ItemPage<PostView> list(@RequestParam(required = false) String page,
            @RequestParam(required = false) String size, @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String category, @RequestParam(defaultValue = "") String state) {
        return posts.list(false, Paging.of(page, size, 20), search, category, state);
    }

    /**
     * 빈 초안 생성
     *
     * @return 생성한 초안
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DraftView create(CurrentUser user) {
        return posts.create(user.id());
    }

    /**
     * 분류 등록
     *
     * @return 등록한 분류
     */
    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest body) {
        return categories.save(body.name(), null);
    }

    /**
     * 분류 이름 변경
     *
     * @return 변경한 분류
     */
    @PatchMapping("/categories/{id}")
    public CategoryResponse renameCategory(@PathVariable UUID id, @Valid @RequestBody CategoryRequest body) {
        return categories.save(body.name(), id);
    }

    /**
     * 분류 삭제
     *
     * @return {@code {success: true}}
     */
    @DeleteMapping("/categories/{id}")
    public Map<String, Boolean> removeCategory(@PathVariable UUID id) {
        categories.remove(id);
        return Map.of("success", true);
    }

    /**
     * 초안 조회
     *
     * @return 편집 화면용 초안
     */
    @GetMapping("/{id}/draft")
    public DraftView draft(@PathVariable UUID id) {
        return posts.draft(id);
    }

    /**
     * 초안 저장
     *
     * @return 저장 후 초안
     */
    @PatchMapping("/{id}/draft")
    public DraftView saveDraft(@PathVariable UUID id, @Valid @RequestBody DraftRequest body) {
        return posts.saveDraft(id, body);
    }

    /**
     * 발행
     *
     * @return 발행 후 초안
     */
    @PostMapping("/{id}/publish")
    @ResponseStatus(HttpStatus.CREATED)
    public DraftView publish(@PathVariable UUID id, @Valid @RequestBody RevisionRequest body, CurrentUser user) {
        return posts.publish(id, body.revision(), user.id());
    }

    /**
     * 비공개
     *
     * @return 변경 후 초안
     */
    @PostMapping("/{id}/hide")
    @ResponseStatus(HttpStatus.CREATED)
    public DraftView hide(@PathVariable UUID id, @Valid @RequestBody RevisionRequest body, CurrentUser user) {
        return posts.hide(id, body.revision(), user.id());
    }

    /**
     * 게시글 삭제
     *
     * @return {@code {success: true}}
     */
    @DeleteMapping("/{id}")
    public Map<String, Boolean> remove(@PathVariable UUID id, CurrentUser user) {
        posts.remove(id, user.id());
        return Map.of("success", true);
    }

    /**
     * 전체 댓글 목록(숨긴 댓글 포함)
     *
     * @return 작성 순
     */
    @GetMapping("/{id}/comments")
    public List<CommentResponse> comments(@PathVariable UUID id) {
        return comments.list(id, true);
    }

    /**
     * 댓글 숨김
     *
     * @return {@code {success: true}}
     */
    @DeleteMapping("/{id}/comments/{commentId}")
    public Map<String, Boolean> moderate(@PathVariable UUID id, @PathVariable UUID commentId, CurrentUser user) {
        comments.remove(id, commentId, user, true);
        return Map.of("success", true);
    }
}

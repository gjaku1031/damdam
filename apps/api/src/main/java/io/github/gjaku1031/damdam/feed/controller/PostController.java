package io.github.gjaku1031.damdam.feed.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.gjaku1031.damdam.common.auth.CurrentUser;
import io.github.gjaku1031.damdam.common.auth.LoginRequired;
import io.github.gjaku1031.damdam.common.web.ItemPage;
import io.github.gjaku1031.damdam.common.web.Paging;
import io.github.gjaku1031.damdam.feed.dto.AdjacentPostsResponse;
import io.github.gjaku1031.damdam.feed.dto.CategoryResponse;
import io.github.gjaku1031.damdam.feed.dto.CommentRequest;
import io.github.gjaku1031.damdam.feed.dto.CommentResponse;
import io.github.gjaku1031.damdam.feed.dto.PostView;
import io.github.gjaku1031.damdam.feed.service.PostCategoryService;
import io.github.gjaku1031.damdam.feed.service.PostCommentService;
import io.github.gjaku1031.damdam.feed.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 공개 블로그 API
 */
@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public final class PostController {
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
     * 공개 게시글 목록
     *
     * @param category 분류 ID
     * @return 발행본 요약, 본문 HTML 제외
     */
    @GetMapping
    public ItemPage<PostView> list(@RequestParam(required = false) String page,
            @RequestParam(required = false) String size, @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "") String category) {
        return posts.list(true, Paging.of(page, size, 20), search, category, "");
    }

    /**
     * 분류 목록
     *
     * @return 이름순
     */
    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return categories.list();
    }

    /**
     * 공개 게시글 상세
     *
     * @return 발행 본문 HTML 포함
     */
    @GetMapping("/{id}")
    public PostView detail(@PathVariable UUID id) {
        return posts.publicDetail(id);
    }

    /**
     * 공개 게시글의 이전·다음 글
     *
     * @return 발행 순서상 바로 앞뒤 공개 글, 없으면 각각 {@code null}
     */
    @GetMapping("/{id}/adjacent")
    public AdjacentPostsResponse adjacent(@PathVariable UUID id) {
        return posts.adjacent(id);
    }

    /**
     * 공개 댓글 목록
     *
     * @return 숨김·삭제 제외, 작성 순
     */
    @GetMapping("/{id}/comments")
    public List<CommentResponse> comments(@PathVariable UUID id) {
        return comments.list(id, false);
    }

    /**
     * 댓글 작성
     *
     * @return 작성한 댓글
     */
    @LoginRequired
    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse comment(@PathVariable UUID id, @Valid @RequestBody CommentRequest body, CurrentUser user) {
        return comments.add(id, user, body.content());
    }

    /**
     * 본인 댓글 삭제
     *
     * @return {@code {success: true}}
     */
    @LoginRequired
    @DeleteMapping("/{id}/comments/{commentId}")
    public Map<String, Boolean> removeComment(@PathVariable UUID id, @PathVariable UUID commentId, CurrentUser user) {
        comments.remove(id, commentId, user, false);
        return Map.of("success", true);
    }
}

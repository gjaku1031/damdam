package io.github.gjaku1031.damdam.feed.service;

import static io.github.gjaku1031.damdam.jooq.Tables.ADMIN_UPDATES;
import static io.github.gjaku1031.damdam.jooq.Tables.POST_COMMENTS;
import static io.github.gjaku1031.damdam.jooq.Tables.USERS;

import java.util.List;
import java.util.UUID;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.gjaku1031.damdam.common.auth.CurrentUser;
import io.github.gjaku1031.damdam.common.error.ApiException;
import io.github.gjaku1031.damdam.feed.domain.FeedPostCommentEntity;
import io.github.gjaku1031.damdam.feed.domain.PostState;
import io.github.gjaku1031.damdam.feed.dto.CommentResponse;
import io.github.gjaku1031.damdam.feed.repository.FeedPostCommentRepository;
import io.github.gjaku1031.damdam.system.domain.AdminAuditAction;
import io.github.gjaku1031.damdam.system.service.SystemService;
import lombok.RequiredArgsConstructor;

/**
 * 게시글 댓글 작성·삭제·운영자 숨김
 *
 * 게시글의 {@code commentCount}는 공개 댓글(삭제·숨김 제외) 수로 유지
 */
@Service
@RequiredArgsConstructor
public class PostCommentService {
    /**
     * 게시글 조회
     */
    private final PostService posts;

    /**
     * 댓글 저장소
     */
    private final FeedPostCommentRepository comments;

    /**
     * 댓글 목록·댓글 수 갱신용 jOOQ
     */
    private final DSLContext dsl;

    /**
     * 운영 기록
     */
    private final SystemService audit;

    /**
     * 댓글 목록
     *
     * @param admin {@code true}면 비공개 글·숨긴 댓글도 포함
     * @return 작성 순, 삭제된 댓글 제외
     * @throws ApiException 게시글이 없거나 공개 글이 아닐 때(404)
     */
    @Transactional(readOnly = true)
    public List<CommentResponse> list(UUID postId, boolean admin) {
        posts.find(postId, !admin);
        Condition condition = POST_COMMENTS.POST_ID.eq(postId).and(POST_COMMENTS.DELETEDAT.isNull());
        if (!admin) condition = condition.and(POST_COMMENTS.HIDDENAT.isNull());
        return dsl.select(POST_COMMENTS.ID, POST_COMMENTS.POST_ID, POST_COMMENTS.USER_ID, POST_COMMENTS.CONTENT,
                        POST_COMMENTS.CREATEDAT, POST_COMMENTS.HIDDENAT, USERS.ID, USERS.USERNAME)
                .from(POST_COMMENTS)
                .leftJoin(USERS).on(USERS.ID.eq(POST_COMMENTS.USER_ID))
                .where(condition)
                .orderBy(POST_COMMENTS.CREATEDAT.asc(), POST_COMMENTS.ID.asc())
                .fetch(row -> new CommentResponse(row.value1(), row.value2(), row.value3(), row.value4(), row.value5(),
                        row.value6(), row.value7() == null ? null : new CommentResponse.Author(row.value7(), row.value8())));
    }

    /**
     * 댓글 작성
     *
     * 게시글 행을 잠근 뒤 댓글 저장과 댓글 수 증가를 함께 수행
     *
     * @param user 현재 사용자, 댓글 제한 여부는 이번 요청에서 DB로 다시 읽은 값
     * @return 작성한 댓글
     * @throws ApiException 댓글 제한 계정(403), 빈 내용(400), 공개 발행 글이 아닐 때(404)
     */
    @Transactional
    public CommentResponse add(UUID postId, CurrentUser user, String content) {
        if (user.isCommunityBlocked()) throw ApiException.forbidden("댓글 작성이 제한된 계정입니다.");
        String trimmed = content.trim();
        if (trimmed.isEmpty()) throw ApiException.badRequest("댓글 내용을 입력해주세요.");
        dsl.selectOne().from(ADMIN_UPDATES)
                .where(ADMIN_UPDATES.ID.eq(postId), ADMIN_UPDATES.VISIBLE.isTrue(),
                        ADMIN_UPDATES.STATE.eq(PostState.PUBLISHED.name()), ADMIN_UPDATES.DELETEDAT.isNull())
                .forUpdate().fetchOptional().orElseThrow(ApiException::notFound);

        FeedPostCommentEntity comment = comments.saveAndFlush(FeedPostCommentEntity.write(postId, user.id(), trimmed));
        dsl.update(ADMIN_UPDATES).set(ADMIN_UPDATES.COMMENTCOUNT, ADMIN_UPDATES.COMMENTCOUNT.plus(1))
                .where(ADMIN_UPDATES.ID.eq(postId)).execute();
        return new CommentResponse(comment.getId(), postId, user.id(), comment.getContent(), comment.getCreatedAt(),
                comment.getHiddenAt(), new CommentResponse.Author(user.id(), user.username()));
    }

    /**
     * 댓글 삭제(작성자) 또는 숨김(운영자)
     *
     * 1. 게시글 확인(작성자 삭제는 공개 글만)
     * 2. 댓글 잠금 후 권한 확인
     * 3. 삭제·숨김 기록, 공개 댓글이었으면 댓글 수 감소(0 미만 방지)
     *
     * @param moderation {@code true}면 운영자 숨김
     * @throws ApiException 게시글·댓글 없음(404), 권한 없음(403)
     */
    @Transactional
    public void remove(UUID postId, UUID commentId, CurrentUser user, boolean moderation) {
        posts.find(postId, !moderation);
        FeedPostCommentEntity comment = comments.findActiveForUpdate(commentId, postId)
                .orElseThrow(ApiException::notFound);
        if (moderation ? !user.isAdmin() : !comment.isOwnedBy(user.id())) throw ApiException.forbidden();

        // 이미 숨긴 댓글은 댓글 수에 포함되지 않으므로 다시 빼지 않음
        boolean counted = !comment.isHidden();
        if (moderation) comment.hideBy(user.id());
        else comment.deleteBy(user.id());
        comments.flush();
        if (counted) {
            dsl.update(ADMIN_UPDATES)
                    .set(ADMIN_UPDATES.COMMENTCOUNT, DSL.greatest(ADMIN_UPDATES.COMMENTCOUNT.minus(1), DSL.inline(0)))
                    .where(ADMIN_UPDATES.ID.eq(postId)).execute();
        }
        if (moderation) audit.log(user.id(), AdminAuditAction.UPDATE, "COMMENT", commentId.toString(), "댓글 숨김");
    }
}

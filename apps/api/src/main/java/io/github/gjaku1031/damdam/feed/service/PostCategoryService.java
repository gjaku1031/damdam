package io.github.gjaku1031.damdam.feed.service;

import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.gjaku1031.damdam.common.error.ApiException;
import io.github.gjaku1031.damdam.feed.domain.PostCategoryEntity;
import io.github.gjaku1031.damdam.feed.dto.CategoryResponse;
import io.github.gjaku1031.damdam.feed.repository.PostCategoryRepository;
import lombok.RequiredArgsConstructor;

/**
 * 게시글 분류 관리
 */
@Service
@RequiredArgsConstructor
public class PostCategoryService {
    /**
     * 분류를 지울 때 발행본·초안의 분류를 함께 비우는 SQL
     *
     * 바뀐 글은 리비전을 올려 열려 있는 편집 화면의 저장을 막음
     */
    private static final String DETACH_CATEGORY_SQL = """
            UPDATE admin_updates
               SET "categoryId" = CASE WHEN "categoryId" = ? THEN NULL ELSE "categoryId" END,
                   draft = CASE WHEN draft->>'categoryId' = ? THEN jsonb_set(draft, '{categoryId}', 'null') ELSE draft END,
                   revision = revision + 1
             WHERE "categoryId" = ? OR draft->>'categoryId' = ?""";

    /**
     * 분류 저장소
     */
    private final PostCategoryRepository categories;

    /**
     * 게시글 일괄 갱신용 jOOQ
     */
    private final DSLContext dsl;

    /**
     * 이름 중복을 트랜잭션 밖에서 변환하기 위한 실행기
     */
    private final TransactionTemplate transaction;

    /**
     * 전체 분류 조회
     *
     * @return 이름순
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categories.findAll(Sort.by("name")).stream().map(CategoryResponse::from).toList();
    }

    /**
     * 분류 등록 또는 이름 변경
     *
     * @param id 바꿀 분류 ID, 등록이면 {@code null}
     * @return 저장한 분류
     * @throws ApiException 이름이 비어 있을 때(400), 분류가 없을 때(404), 이름이 중복될 때(409)
     */
    public CategoryResponse save(String name, UUID id) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) throw ApiException.badRequest("분류명을 입력해주세요.");
        try {
            return transaction.execute(status -> {
                PostCategoryEntity category = id == null ? PostCategoryEntity.named(trimmed)
                        : categories.findById(id).orElseThrow(() -> ApiException.notFound("분류를 찾을 수 없습니다."));
                category.rename(trimmed);
                return CategoryResponse.from(categories.saveAndFlush(category));
            });
        } catch (DataIntegrityViolationException duplicate) {
            throw ApiException.conflict("이미 있는 분류입니다.");
        }
    }

    /**
     * 분류 삭제
     *
     * 게시글 발행본·초안의 분류를 먼저 비운 뒤 분류 삭제, 실패 시 함께 롤백
     *
     * @throws ApiException 분류가 없을 때(404)
     */
    @Transactional
    public void remove(UUID id) {
        PostCategoryEntity category = categories.findById(id)
                .orElseThrow(() -> ApiException.notFound("분류를 찾을 수 없습니다."));
        dsl.execute(DETACH_CATEGORY_SQL, id, id.toString(), id, id.toString());
        categories.delete(category);
    }
}

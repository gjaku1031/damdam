package io.github.gjaku1031.damdam.feed.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.github.gjaku1031.damdam.feed.domain.PostState;

/**
 * 게시글 응답
 *
 * 공개 응답은 발행본 기준이며 상태·리비전을 담지 않음
 * 운영 응답은 제목·대표 사진에 작성 중인 초안 값을 우선 사용
 *
 * @param id ID
 * @param title 제목
 * @param excerpt 발행 본문의 앞 140자(태그 제거)
 * @param bodyHtml 발행 본문 HTML, 목록 응답이면 키 생략
 * @param coverObjectKey 대표 사진 키
 * @param categoryId 분류 ID
 * @param category 분류, 없으면 {@code null}
 * @param tags 태그
 * @param commentCount 공개 댓글 수
 * @param createdAt 생성 시각
 * @param updatedAt 공개 응답은 마지막 발행 시각, 운영 응답은 행 수정 시각
 * @param publishedAt 최초 발행 시각
 * @param state 상태, 운영 응답에만 포함
 * @param revision 낙관적 잠금 리비전, 운영 응답에만 포함
 */
public record PostView(UUID id, String title, String excerpt, @JsonInclude(Include.NON_NULL) String bodyHtml,
        String coverObjectKey, UUID categoryId, CategoryResponse category, List<String> tags, int commentCount,
        Instant createdAt, Instant updatedAt, Instant publishedAt, @JsonInclude(Include.NON_NULL) PostState state,
        @JsonInclude(Include.NON_NULL) Integer revision) {}

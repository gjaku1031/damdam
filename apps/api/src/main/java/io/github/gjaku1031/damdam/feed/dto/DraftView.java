package io.github.gjaku1031.damdam.feed.dto;

import java.time.Instant;
import java.util.UUID;

import io.github.gjaku1031.damdam.feed.domain.PostState;
import tools.jackson.databind.JsonNode;

/**
 * 운영자 편집 화면용 초안
 *
 * @param id 게시글 ID
 * @param state 상태
 * @param revision 저장·발행 시 보내야 하는 현재 리비전
 * @param publishedAt 최초 발행 시각
 * @param draft 초안({@code title, documentJson, tags, categoryId, coverObjectKey}), 초안이 없으면 발행본으로 구성
 */
public record DraftView(UUID id, PostState state, int revision, Instant publishedAt, JsonNode draft) {}
